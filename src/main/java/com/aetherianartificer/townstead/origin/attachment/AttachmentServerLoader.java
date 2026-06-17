package com.aetherianartificer.townstead.origin.attachment;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.pheno.lang.PhenoDiagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Severity;
import com.aetherianartificer.townstead.pheno.lang.validate.PhenoValidator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Server data loader for attachments. Reads {@code data/<ns>/attachment/<id>.json}
 * definitions plus their referenced geometry ({@code attachment/geo/<name>.geo.json})
 * and texture ({@code attachment/textures/<name>.png}) bytes, hashes the blobs
 * (SHA-1), and fills {@link AttachmentServerData}. Attachment points come from
 * {@code data/<ns>/attachment_point/<id>.json}. The bytes are then synced + cached
 * client-side, so a pack needs no resource pack.
 */
public final class AttachmentServerLoader implements ResourceManagerReloadListener {

    private static final int MAX_TEXTURE_BYTES = 8 * 1024 * 1024;
    private static final String DIR = "attachment";
    private static final String SLOT_DIR = "attachment_point";
    private static final String TEX_DIR = "textures";

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        List<AttachmentDef> defs = new ArrayList<>();
        List<AttachmentPointDef> slots = new ArrayList<>();
        Map<String, AttachmentServerData.Blob> blobs = new LinkedHashMap<>();
        Diagnostics diagnostics = new Diagnostics();

        manager.listResources(DIR, rl -> isTopLevelJson(rl.getPath())).forEach((file, resource) -> {
            String id = idFrom(file.getNamespace(), file.getPath(), DIR);
            JsonObject json = readJson(resource);
            if (id == null || json == null) return;
            PhenoValidator.validateData(file, json, AttachmentSchemas.ATTACHMENT, diagnostics);
            try {
                AttachmentDef def = parseDef(manager, file.getNamespace(), id, json, blobs);
                if (def != null) defs.add(def);
            } catch (Exception e) {
                Townstead.LOGGER.error("Failed to load attachment {}", id, e);
            }
        });

        manager.listResources(SLOT_DIR, rl -> rl.getPath().endsWith(".json")).forEach((file, resource) -> {
            String id = idFrom(file.getNamespace(), file.getPath(), SLOT_DIR);
            JsonObject json = readJson(resource);
            if (id == null || json == null) return;
            PhenoValidator.validateData(file, json, AttachmentSchemas.ATTACHMENT_POINT, diagnostics);
            slots.add(new AttachmentPointDef(id, GsonHelper.getAsString(json, "bone", "body"),
                    readVec(json, "offset"), readTags(json)));
        });

        // Named datapack textures ("data/<ns>/textures/**.png"): rig + face textures shipped to the
        // client over the blob sync (no resource pack). Logical id == the ResourceLocation string a rig
        // or gene references, e.g. "townstead_skeleton:textures/entity/skeletownie.png".
        Map<String, String> namedTextures = new LinkedHashMap<>();
        manager.listResources(TEX_DIR, rl -> rl.getPath().endsWith(".png")).forEach((file, resource) -> {
            byte[] bytes = readBytes(manager, file, MAX_TEXTURE_BYTES);
            if (bytes == null) return;
            try {
                String sha = sha1(bytes);
                blobs.put(sha, new AttachmentServerData.Blob(bytes, AttachmentServerData.KIND_TEXTURE));
                namedTextures.put(file.toString(), sha);
            } catch (Exception e) {
                Townstead.LOGGER.error("Failed to hash datapack texture {}", file, e);
            }
        });

        AttachmentServerData.set(defs, slots, blobs, namedTextures);
        PhenoDiagnostics.replace("attachment", diagnostics.all());
        int errors = diagnostics.count(Severity.ERROR);
        Townstead.LOGGER.info("Loaded {} attachment definitions, {} points, {} blobs ({} diagnostic{})",
                defs.size(), slots.size(), blobs.size(), diagnostics.all().size(),
                diagnostics.all().size() == 1 ? "" : "s");
        if (errors > 0) Townstead.LOGGER.warn("attachment: {} error diagnostic(s); run /pheno validate for detail", errors);
    }

    private static AttachmentDef parseDef(ResourceManager manager, String ns, String id, JsonObject json,
                                          Map<String, AttachmentServerData.Blob> blobs) throws Exception {
        ResourceLocation geoFile = resolve(ns, GsonHelper.getAsString(json, "geometry", ""), "geo", ".geo.json");
        ResourceLocation texFile = resolve(ns, GsonHelper.getAsString(json, "texture", ""), "textures", ".png");
        if (geoFile == null || texFile == null) {
            Townstead.LOGGER.warn("Attachment {} missing geometry/texture reference", id);
            return null;
        }
        byte[] geo = readBytes(manager, geoFile, Integer.MAX_VALUE);
        byte[] tex = readBytes(manager, texFile, MAX_TEXTURE_BYTES);
        if (geo == null || tex == null) return null;

        String geoSha = sha1(geo);
        String texSha = sha1(tex);
        blobs.put(geoSha, new AttachmentServerData.Blob(geo, AttachmentServerData.KIND_GEO));
        blobs.put(texSha, new AttachmentServerData.Blob(tex, AttachmentServerData.KIND_TEXTURE));

        String targetTag = null;
        String targetPoint = null;
        if (json.has("target") && json.get("target").isJsonObject()) {
            JsonObject target = json.getAsJsonObject("target");
            targetTag = emptyToNull(GsonHelper.getAsString(target, "tag", ""));
            targetPoint = emptyToNull(GsonHelper.getAsString(target, "point", ""));
        }
        String bone = GsonHelper.getAsString(json, "bone", "body");
        float scale = GsonHelper.getAsFloat(json, "scale", 1f);
        int tint = parseHex(GsonHelper.getAsString(json, "tint", "#FFFFFF"));
        return new AttachmentDef(id, geoSha, texSha, targetTag, targetPoint, bone,
                readVec(json, "offset"), readVec(json, "rotation"), scale, tint);
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static ResourceLocation resolve(String defNs, String ref, String subdir, String suffix) {
        if (ref == null || ref.isEmpty()) return null;
        int colon = ref.indexOf(':');
        String ns = colon < 0 ? defNs : ref.substring(0, colon);
        String name = colon < 0 ? ref : ref.substring(colon + 1);
        return ResourceLocation.tryParse(ns + ":" + DIR + "/" + subdir + "/" + name + suffix);
    }

    private static byte[] readBytes(ResourceManager manager, ResourceLocation file, int cap) {
        Optional<Resource> resource = manager.getResource(file);
        if (resource.isEmpty()) {
            Townstead.LOGGER.warn("Attachment blob not found: {}", file);
            return null;
        }
        try (InputStream in = resource.get().open()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length > cap) {
                Townstead.LOGGER.warn("Attachment blob {} is {} bytes, over the {} cap; skipped",
                        file, bytes.length, cap);
                return null;
            }
            return bytes;
        } catch (Exception e) {
            Townstead.LOGGER.error("Failed to read attachment blob {}", file, e);
            return null;
        }
    }

    private static boolean isTopLevelJson(String path) {
        return path.startsWith(DIR + "/")
                && path.endsWith(".json")
                && path.indexOf('/', DIR.length() + 1) < 0;
    }

    private static String idFrom(String ns, String path, String dir) {
        String name = path.substring(dir.length() + 1, path.length() - ".json".length());
        return ns + ":" + name;
    }

    private static JsonObject readJson(Resource resource) {
        try (BufferedReader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> readTags(JsonObject json) {
        List<String> tags = new ArrayList<>();
        if (json.has("tags") && json.get("tags").isJsonArray()) {
            for (var element : json.getAsJsonArray("tags")) tags.add(element.getAsString());
        }
        return tags;
    }

    private static float[] readVec(JsonObject json, String key) {
        float[] out = new float[3];
        if (json.has(key) && json.get(key).isJsonArray()) {
            JsonArray array = json.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < array.size(); i++) out[i] = array.get(i).getAsFloat();
        }
        return out;
    }

    private static int parseHex(String raw) {
        String s = raw.startsWith("#") ? raw.substring(1) : raw;
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    private static String sha1(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
    }
}
