package com.aetherianartificer.townstead.devlink;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.root.Root;
import com.aetherianartificer.townstead.root.RootAssignment;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.Species;
import com.aetherianartificer.townstead.root.SpeciesRegistry;
import com.aetherianartificer.townstead.root.attachment.AttachmentDef;
import com.aetherianartificer.townstead.root.attachment.AttachmentPointDef;
import com.aetherianartificer.townstead.root.attachment.AttachmentServerData;
import com.aetherianartificer.townstead.root.gene.InheritedGene;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The Bench Link endpoints (protocol {@value BenchLink#PROTOCOL}). Everything here is read-only:
 * Phase 0 of Benchstead only shows a root, it does not change one. Reads that touch entities or
 * the resource manager hop onto the server thread; blob bytes are immutable and served directly.
 *
 * <p>Authored JSON is re-read from the server's resource manager rather than kept by each loader,
 * so the bundle always shows the file the top-most pack provides, exactly as the loaders saw it.</p>
 */
final class BenchApi implements BenchHttp.Handler {

    private static final long SERVER_TIMEOUT_SECONDS = 5;

    private final MinecraftServer server;

    BenchApi(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public BenchHttp.Response handle(BenchHttp.Request request) throws Exception {
        String path = request.path();
        String method = request.method();
        if (path.equals("/events")) return BenchHttp.Response.sse();
        if (method.equals("GET")) {
            if (path.equals("/status")) return json(onServer(this::status));
            if (path.equals("/roots")) return json(onServer(this::roots));
            if (path.startsWith("/roots/")) {
                ResourceLocation id = ResourceLocation.tryParse(path.substring("/roots/".length()));
                if (id == null) return BenchHttp.Response.error(400, "bad root id");
                JsonObject bundle = onServer(() -> resolvedRoot(id));
                return bundle == null ? BenchHttp.Response.error(404, "no root " + id) : json(bundle);
            }
            if (path.startsWith("/blob/")) return blob(path.substring("/blob/".length()));
            if (path.equals("/file")) return file(request.query().get("id"));
            if (path.equals("/entities")) return json(onServer(this::entities));
            if (path.startsWith("/entities/") && path.endsWith("/anchors")) {
                String raw = path.substring("/entities/".length(), path.length() - "/anchors".length());
                return anchors(raw);
            }
        }
        if (method.equals("POST") && path.equals("/subject")) {
            JsonObject body = JsonParser.parseString(new String(request.body(), StandardCharsets.UTF_8)).getAsJsonObject();
            int id = body.has("id") ? body.get("id").getAsInt() : -1;
            return json(onServer(() -> {
                Entity entity = id < 0 ? null : findEntity(id);
                BenchLink.setSubject(entity);
                JsonObject out = new JsonObject();
                out.addProperty("subject", BenchLink.subjectId());
                return out;
            }));
        }
        return BenchHttp.Response.error(404, "no route " + method + " " + path);
    }

    // --- status ---------------------------------------------------------------------------------

    private JsonObject status() {
        JsonObject out = new JsonObject();
        out.addProperty("protocol", BenchLink.PROTOCOL);
        out.addProperty("mod", Townstead.MOD_ID);
        out.addProperty("version", modVersion());
        out.addProperty("minecraft", server.getServerVersion());
        //? if neoforge {
        out.addProperty("loader", "neoforge");
        //?} else {
        /*out.addProperty("loader", "forge");
        *///?}
        out.addProperty("world", worldName(server));
        out.addProperty("dedicated", server.isDedicatedServer());
        out.addProperty("anchors", BenchAnchors.available());
        JsonArray players = new JsonArray();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) players.add(player.getGameProfile().getName());
        out.add("players", players);
        out.add("subject", subjectJson());
        out.addProperty("sessions", BenchLink.sessions());
        return out;
    }

    private JsonElement subjectJson() {
        int id = BenchLink.subjectId();
        Entity entity = id < 0 ? null : findEntity(id);
        if (entity == null) return com.google.gson.JsonNull.INSTANCE;
        return entityJson(entity, null);
    }

    // --- roots ----------------------------------------------------------------------------------

    private JsonArray roots() {
        List<Root> all = new ArrayList<>(RootRegistry.all());
        all.sort(Comparator.comparing(root -> root.id().toString()));
        JsonArray out = new JsonArray();
        for (Root root : all) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", root.id().toString());
            entry.addProperty("name", text(root.displayName()));
            ResourceLocation species = RootRegistry.effectiveSpecies(root.id());
            addId(entry, "species", species);
            addId(entry, "ancestry", root.ancestry());
            addId(entry, "lineage", root.lineage());
            Species def = species == null ? null : SpeciesRegistry.byId(species);
            if (def != null && def.rig() != null) entry.addProperty("rig", def.rig().base());
            out.add(entry);
        }
        return out;
    }

    /**
     * Everything the plugin needs to draw one root: the chain's authored JSON, the rig with its
     * geometry and texture blobs, the genes the root grants with the attachments each variant can
     * express, those attachments, and every attachment point (the plugin filters them by rig the
     * way the renderer does).
     */
    private @Nullable JsonObject resolvedRoot(ResourceLocation id) {
        Root root = RootRegistry.byId(id);
        if (root == null) return null;
        ResourceManager resources = server.getResourceManager();
        JsonObject out = new JsonObject();
        out.addProperty("protocol", BenchLink.PROTOCOL);

        JsonObject rootJson = layer(resources, id, "root");
        if (rootJson.get("source").isJsonNull()) rootJson = layer(resources, id, "origin");
        rootJson.addProperty("name", text(root.displayName()));
        out.add("root", rootJson);

        ResourceLocation speciesId = RootRegistry.effectiveSpecies(id);
        out.add("species", speciesId == null ? com.google.gson.JsonNull.INSTANCE : layer(resources, speciesId, "species"));
        out.add("ancestry", root.ancestry() == null ? com.google.gson.JsonNull.INSTANCE : layer(resources, root.ancestry(), "ancestry"));
        out.add("lineage", root.lineage() == null ? com.google.gson.JsonNull.INSTANCE : layer(resources, root.lineage(), "lineage"));

        Species species = speciesId == null ? null : SpeciesRegistry.byId(speciesId);
        String rigBase = species == null || species.rig() == null ? "mca:villager" : species.rig().base();
        out.add("rig", rigJson(resources, rigBase, species == null || species.rig() == null ? 1f : species.rig().scale()));

        Set<String> attachmentIds = new LinkedHashSet<>();
        JsonArray genes = new JsonArray();
        for (InheritedGene inherited : RootRegistry.effectiveInheritedGenes(id)) {
            JsonObject gene = new JsonObject();
            gene.addProperty("id", inherited.geneId().toString());
            gene.addProperty("occurrence", inherited.occurrence());
            JsonObject source = readJson(resources, file(inherited.geneId(), "gene"));
            gene.addProperty("type", source == null ? "" : stringOr(source, "type", ""));
            JsonObject variants = attachmentVariants(source, inherited.geneId().getNamespace());
            gene.add("attachments", variants);
            for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
                for (JsonElement attachment : variant.getValue().getAsJsonArray()) attachmentIds.add(attachment.getAsString());
            }
            gene.add("source", source == null ? com.google.gson.JsonNull.INSTANCE : source);
            genes.add(gene);
        }
        out.add("genes", genes);

        JsonObject attachments = new JsonObject();
        Map<String, AttachmentDef> defs = new LinkedHashMap<>();
        for (AttachmentDef def : AttachmentServerData.definitions()) defs.put(def.id(), def);
        for (String attachmentId : attachmentIds) {
            AttachmentDef def = defs.get(attachmentId);
            if (def != null) attachments.add(attachmentId, attachmentJson(resources, def));
        }
        out.add("attachments", attachments);

        JsonArray points = new JsonArray();
        for (AttachmentPointDef point : AttachmentServerData.slots()) {
            JsonObject p = new JsonObject();
            p.addProperty("id", point.id());
            p.addProperty("bone", point.bone());
            p.add("offset", vec(point.offset()));
            p.add("rotation", vec(point.rotation()));
            p.addProperty("mirror", point.mirror());
            p.addProperty("rig", point.rig());
            JsonArray tags = new JsonArray();
            point.tags().forEach(tags::add);
            p.add("tags", tags);
            points.add(p);
        }
        out.add("points", points);
        return out;
    }

    private JsonObject rigJson(ResourceManager resources, String base, float scale) {
        JsonObject rig = new JsonObject();
        rig.addProperty("id", base);
        rig.addProperty("scale", scale);
        ResourceLocation rigId = ResourceLocation.tryParse(base);
        JsonObject source = rigId == null ? null : readJson(resources, file(rigId, "rig"));
        rig.add("source", source == null ? com.google.gson.JsonNull.INSTANCE : source);
        String kind = "humanoid";
        if (source != null && source.has("model") && source.get("model").isJsonObject()) {
            JsonObject model = source.getAsJsonObject("model");
            kind = stringOr(model, "type", "entity_layer");
            String geometry = stringOr(model, "file", "");
            if (!geometry.isEmpty()) {
                rig.addProperty("geometryFile", geometry);
                String sha = AttachmentServerData.namedGeo().get(geometry);
                if (sha != null) rig.addProperty("geoSha1", sha);
            }
        }
        String texture = source == null ? "" : stringOr(source, "texture", "");
        if (!texture.isEmpty()) {
            rig.addProperty("textureFile", texture);
            String sha = AttachmentServerData.namedTextures().get(texture);
            if (sha != null) rig.addProperty("textureSha1", sha);
        }
        rig.addProperty("kind", kind);
        return rig;
    }

    private static JsonObject attachmentJson(ResourceManager resources, AttachmentDef def) {
        JsonObject json = new JsonObject();
        json.addProperty("id", def.id());
        json.addProperty("geoSha1", def.geoSha1());
        json.addProperty("textureSha1", def.textureSha1());
        if (def.targetTag() != null) json.addProperty("targetTag", def.targetTag());
        if (def.targetPoint() != null) json.addProperty("targetPoint", def.targetPoint());
        json.addProperty("bone", def.bone());
        json.add("offset", vec(def.offset()));
        json.add("rotation", vec(def.rotation()));
        json.addProperty("scale", def.scale());
        JsonObject stages = new JsonObject();
        for (Map.Entry<String, AttachmentDef.StageOverride> entry : def.stages().entrySet()) {
            JsonObject stage = new JsonObject();
            stage.addProperty("scale", entry.getValue().scale());
            stage.add("offset", vec(entry.getValue().offset()));
            if (entry.getValue().geoSha1() != null) stage.addProperty("geoSha1", entry.getValue().geoSha1());
            stages.add(entry.getKey(), stage);
        }
        json.add("stages", stages);
        JsonObject source = AttachmentServerData.sourceJson(def.id());
        json.add("source", source == null ? com.google.gson.JsonNull.INSTANCE : source);
        if (source != null) {
            String ns = def.id().contains(":") ? def.id().substring(0, def.id().indexOf(':')) : "minecraft";
            String geometry = geometryFile(resources, ns, stringOr(source, "geometry", ""));
            if (geometry != null) json.addProperty("geometryFile", geometry);
        }
        return json;
    }

    /**
     * The file a definition's {@code geometry} reference loads, using the attachment loader's
     * lookup: {@code attachment/geo/<name>.geo.json} first, then {@code attachment/bbmodel/<name>.bbmodel}.
     */
    private static @Nullable String geometryFile(ResourceManager resources, String defaultNs, String ref) {
        if (ref.isEmpty()) return null;
        int hash = ref.indexOf('#');
        String name = hash < 0 ? ref : ref.substring(0, hash);
        int colon = name.indexOf(':');
        String ns = colon < 0 ? defaultNs : name.substring(0, colon);
        String path = colon < 0 ? name : name.substring(colon + 1);
        for (String candidate : new String[]{"attachment/geo/" + path + ".geo.json", "attachment/bbmodel/" + path + ".bbmodel"}) {
            ResourceLocation file = ResourceLocation.tryParse(ns + ":" + candidate);
            if (file != null && resources.getResource(file).isPresent()) return file.toString();
        }
        return null;
    }

    /**
     * Variant id to the attachment ids it expresses. A gene without variants reports its single
     * form under {@code ""}. Ids without a namespace take the gene's.
     */
    private static JsonObject attachmentVariants(@Nullable JsonObject gene, String ns) {
        JsonObject out = new JsonObject();
        if (gene == null) return out;
        JsonArray top = attachmentRefs(gene, ns);
        if (!top.isEmpty()) out.add("", top);
        if (gene.has("variants") && gene.get("variants").isJsonObject()) {
            for (Map.Entry<String, JsonElement> variant : gene.getAsJsonObject("variants").entrySet()) {
                if (!variant.getValue().isJsonObject()) continue;
                JsonArray refs = attachmentRefs(variant.getValue().getAsJsonObject(), ns);
                if (!refs.isEmpty()) out.add(variant.getKey(), refs);
            }
        }
        return out;
    }

    private static JsonArray attachmentRefs(JsonObject json, String ns) {
        JsonArray out = new JsonArray();
        if (json.has("attachment") && json.get("attachment").isJsonPrimitive()) out.add(qualify(json.get("attachment").getAsString(), ns));
        if (json.has("attachments") && json.get("attachments").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("attachments")) {
                if (element.isJsonPrimitive()) out.add(qualify(element.getAsString(), ns));
            }
        }
        return out;
    }

    private static String qualify(String id, String ns) {
        return id.contains(":") ? id : ns + ":" + id;
    }

    // --- entities -------------------------------------------------------------------------------

    private JsonObject entities() {
        Map<Integer, JsonObject> found = new LinkedHashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(48),
                    e -> e instanceof VillagerEntityMCA || e instanceof ServerPlayer)) {
                found.putIfAbsent(entity.getId(), entityJson(entity, player));
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("subject", BenchLink.subjectId());
        JsonArray list = new JsonArray();
        found.values().stream()
                .sorted(Comparator.comparingDouble(e -> e.get("distance").getAsDouble()))
                .forEach(list::add);
        out.add("entities", list);
        return out;
    }

    private static JsonObject entityJson(Entity entity, @Nullable ServerPlayer near) {
        JsonObject json = new JsonObject();
        json.addProperty("id", entity.getId());
        json.addProperty("uuid", entity.getUUID().toString());
        json.addProperty("name", entity.getName().getString());
        json.addProperty("kind", entity instanceof ServerPlayer ? "player" : "villager");
        String root = RootAssignment.currentRoot(entity);
        if (root != null) json.addProperty("root", root);
        json.addProperty("x", entity.getX());
        json.addProperty("y", entity.getY());
        json.addProperty("z", entity.getZ());
        json.addProperty("distance", near == null ? 0 : near.distanceTo(entity));
        return json;
    }

    private @Nullable Entity findEntity(int id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    // --- anchors --------------------------------------------------------------------------------

    private BenchHttp.Response anchors(String rawId) throws InterruptedException {
        int id;
        try {
            id = Integer.parseInt(rawId);
        } catch (NumberFormatException e) {
            return BenchHttp.Response.error(400, "bad entity id");
        }
        if (!BenchAnchors.available()) {
            return BenchHttp.Response.error(501, "anchors need a game client in this process (single player)");
        }
        JsonObject capture = BenchAnchors.capture(id, 2000);
        if (capture == null) return BenchHttp.Response.error(404, "entity " + id + " was not rendered; look at it in game");
        return json(capture);
    }

    // --- files ----------------------------------------------------------------------------------

    private static BenchHttp.Response blob(String sha) {
        AttachmentServerData.Blob blob = AttachmentServerData.blob(sha);
        if (blob == null) return BenchHttp.Response.error(404, "no blob " + sha);
        String type = blob.kind() == AttachmentServerData.KIND_TEXTURE ? "image/png" : "application/json";
        return BenchHttp.Response.bytes(type, blob.bytes());
    }

    /** Raw bytes of one data-pack file ({@code ns:path} under {@code data/}), for the plugin's source view. */
    private BenchHttp.Response file(@Nullable String rawId) throws Exception {
        ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
        if (id == null || id.getPath().contains("..")) return BenchHttp.Response.error(400, "bad file id");
        String path = id.getPath().toLowerCase(Locale.ROOT);
        String type;
        if (path.endsWith(".png")) type = "image/png";
        else if (path.endsWith(".json") || path.endsWith(".bbmodel")) type = "application/json";
        else return BenchHttp.Response.error(403, "only .json, .bbmodel and .png files are served");
        Optional<Resource> resource = onServer(() -> server.getResourceManager().getResource(id));
        if (resource.isEmpty()) return BenchHttp.Response.error(404, "no file " + id);
        byte[] bytes;
        try (InputStream in = resource.get().open()) {
            bytes = in.readAllBytes();
        }
        return new BenchHttp.Response(200, type, bytes, Map.of("X-Bench-Pack", resource.get().sourcePackId()));
    }

    // --- helpers --------------------------------------------------------------------------------

    static String worldName(MinecraftServer server) {
        return server.getWorldData().getLevelName();
    }

    private <T> T onServer(Supplier<T> work) throws Exception {
        if (server.isSameThread()) return work.get();
        CompletableFuture<T> future = server.submit(work);
        return future.get(SERVER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static BenchHttp.Response json(JsonElement element) {
        return BenchHttp.Response.json(200, element.toString());
    }

    private static JsonObject layer(ResourceManager resources, ResourceLocation id, String dir) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        JsonObject source = readJson(resources, file(id, dir));
        json.addProperty("file", file(id, dir).toString());
        json.add("source", source == null ? com.google.gson.JsonNull.INSTANCE : source);
        return json;
    }

    private static ResourceLocation file(ResourceLocation id, String dir) {
        return ResourceLocation.tryParse(id.getNamespace() + ":" + dir + "/" + id.getPath() + ".json");
    }

    private static @Nullable JsonObject readJson(ResourceManager resources, @Nullable ResourceLocation file) {
        if (file == null) return null;
        Optional<Resource> resource = resources.getResource(file);
        if (resource.isEmpty()) return null;
        try (Reader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            Townstead.LOGGER.warn("Bench Link could not read {}", file, e);
            return null;
        }
    }

    private static String stringOr(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static void addId(JsonObject json, String key, @Nullable ResourceLocation id) {
        if (id != null) json.addProperty(key, id.toString());
    }

    private static JsonArray vec(float[] values) {
        JsonArray array = new JsonArray();
        for (float value : values) array.add(value);
        return array;
    }

    private static String text(@Nullable Component component) {
        return component == null ? "" : component.getString();
    }

    private static String modVersion() {
        //? if neoforge {
        return net.neoforged.fml.ModList.get().getModContainerById(Townstead.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("unknown");
        //?} else {
        /*return net.minecraftforge.fml.ModList.get().getModContainerById(Townstead.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("unknown");
        *///?}
    }
}
