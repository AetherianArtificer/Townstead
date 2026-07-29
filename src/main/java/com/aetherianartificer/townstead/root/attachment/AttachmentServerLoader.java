package com.aetherianartificer.townstead.root.attachment;

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
    private static final int MAX_GEO_BYTES = 4 * 1024 * 1024;
    private static final String DIR = "attachment";
    private static final String SLOT_DIR = "attachment_point";
    private static final String TEX_DIR = "textures";
    private static final String GEO_DIR = "geo";
    private static final String BB_DIR = "bbmodel";

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        List<AttachmentDef> defs = new ArrayList<>();
        List<AttachmentPointDef> slots = new ArrayList<>();
        Map<String, AttachmentServerData.Blob> blobs = new LinkedHashMap<>();
        Map<String, JsonObject> sources = new LinkedHashMap<>();
        Diagnostics diagnostics = new Diagnostics();

        manager.listResources(DIR, rl -> isTopLevelJson(rl.getPath())).forEach((file, resource) -> {
            String id = idFrom(file.getNamespace(), file.getPath(), DIR);
            JsonObject json = readJson(resource);
            if (id == null || json == null) return;
            PhenoValidator.validateData(file, json, AttachmentSchemas.ATTACHMENT, diagnostics);
            try {
                AttachmentDef def = parseDef(manager, file.getNamespace(), id, json, blobs);
                if (def != null) {
                    defs.add(def);
                    sources.put(id, json);
                }
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
                    readVec(json, "offset"), readTags(json),
                    readVec(json, "rotation"),
                    GsonHelper.getAsBoolean(json, "mirror", false),
                    GsonHelper.getAsString(json, "rig", "")));
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

        // Named datapack geometry ("data/<ns>/geo/**.geo.json"): a custom-geometry rig's model, shipped to
        // the client over the same blob sync. Logical id == the ResourceLocation string a rig's
        // model.file references, e.g. "townstead_spider:geo/egg.geo.json".
        Map<String, String> namedGeo = new LinkedHashMap<>();
        manager.listResources(GEO_DIR, rl -> rl.getPath().endsWith(".geo.json")).forEach((file, resource) -> {
            byte[] bytes = readBytes(manager, file, MAX_GEO_BYTES);
            if (bytes == null) return;
            try {
                String sha = sha1(bytes);
                blobs.put(sha, new AttachmentServerData.Blob(bytes, AttachmentServerData.KIND_GEO));
                namedGeo.put(file.toString(), sha);
            } catch (Exception e) {
                Townstead.LOGGER.error("Failed to hash datapack geometry {}", file, e);
            }
        });

        // Named datapack Blockbench projects ("data/<ns>/bbmodel/**.bbmodel"): converted to the same
        // geo dialect at load, so a rig's model.file can reference the project directly, e.g.
        // "townstead_insects:bbmodel/spooder.bbmodel" — no hand-converted .geo.json needed.
        manager.listResources(BB_DIR, rl -> rl.getPath().endsWith(".bbmodel")).forEach((file, resource) -> {
            byte[] bytes = readBytes(manager, file, Integer.MAX_VALUE);
            if (bytes == null) return;
            String path = file.getPath();
            String name = path.substring(path.lastIndexOf('/') + 1, path.length() - ".bbmodel".length());
            byte[] geo = BbmodelConverter.geometry(bytes, name, "");
            if (geo == null) return;
            try {
                String sha = sha1(geo);
                blobs.put(sha, new AttachmentServerData.Blob(geo, AttachmentServerData.KIND_GEO));
                namedGeo.put(file.toString(), sha);
            } catch (Exception e) {
                Townstead.LOGGER.error("Failed to hash datapack bbmodel {}", file, e);
            }
        });

        AttachmentServerData.set(defs, slots, blobs, namedTextures, namedGeo, sources);
        PhenoDiagnostics.replace("attachment", diagnostics.all());
        int errors = diagnostics.count(Severity.ERROR);
        Townstead.LOGGER.info("Loaded {} attachment definitions, {} points, {} blobs ({} diagnostic{})",
                defs.size(), slots.size(), blobs.size(), diagnostics.all().size(),
                diagnostics.all().size() == 1 ? "" : "s");
        if (errors > 0) Townstead.LOGGER.warn("attachment: {} error diagnostic(s); run /pheno validate for detail", errors);
        // Health checks that only need the attachment data itself (gene cross-checks join a registry
        // that may not have reloaded yet; those run in /townstead attachment doctor instead).
        List<String> health = AttachmentDoctor.selfChecks();
        for (String problem : health) Townstead.LOGGER.warn("attachment: {}", problem);
        if (!health.isEmpty()) {
            Townstead.LOGGER.warn("attachment: {} problem(s) above; run /townstead attachment doctor for the full report",
                    health.size());
        }
    }

    private static AttachmentDef parseDef(ResourceManager manager, String ns, String id, JsonObject json,
                                          Map<String, AttachmentServerData.Blob> blobs) throws Exception {
        String geometryRef = GsonHelper.getAsString(json, "geometry", "");
        ResourceLocation texFile = resolve(ns, GsonHelper.getAsString(json, "texture", ""), "textures", ".png");
        if (geometryRef.isEmpty() || texFile == null) {
            Townstead.LOGGER.warn("Attachment {} missing geometry/texture reference", id);
            return null;
        }
        byte[] geo = geometryBytes(manager, ns, geometryRef);
        byte[] tex = readBytes(manager, texFile, MAX_TEXTURE_BYTES);
        if (geo == null) {
            Townstead.LOGGER.warn("Attachment {} geometry '{}' not found (.geo.json or .bbmodel)", id, geometryRef);
            return null;
        }
        if (tex == null) return null;

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
        // "tint" is a hex colour or a bearer-resolved source; "skin_tint": true is the legacy alias.
        String tintRaw = GsonHelper.getAsString(json, "tint", "#FFFFFF");
        int tintSource = switch (tintRaw) {
            case "skin" -> AttachmentDef.TINT_SKIN;
            case "hair" -> AttachmentDef.TINT_HAIR;
            case "eyes" -> AttachmentDef.TINT_EYES;
            case "gene" -> AttachmentDef.TINT_GENE;
            default -> AttachmentDef.TINT_FLAT;
        };
        int tint = tintSource == AttachmentDef.TINT_FLAT ? parseHex(tintRaw) : 0xFFFFFF;
        if (GsonHelper.getAsBoolean(json, "skin_tint", false)) tintSource = AttachmentDef.TINT_SKIN;
        int tintBlend = switch (GsonHelper.getAsString(json, "tint_blend", "multiply")) {
            case "screen" -> 1;
            case "overlay" -> 2;
            case "color" -> 3;
            default -> 0;
        };
        float tintStrength = Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(json, "tint_strength", 1f)));
        // A grayscale mask gating the tint per pixel, same shape as the emissive layer: an
        // optional second PNG under attachment/textures, blob-synced and sampled in the bake.
        String tintMaskSha = "";
        String tintMaskRef = GsonHelper.getAsString(json, "tint_mask", "");
        if (!tintMaskRef.isEmpty()) {
            ResourceLocation maskFile = resolve(ns, tintMaskRef, "textures", ".png");
            byte[] mask = maskFile == null ? null : readBytes(manager, maskFile, MAX_TEXTURE_BYTES);
            if (mask == null) {
                Townstead.LOGGER.warn("Attachment {} tint_mask '{}' not found; the tint applies unmasked",
                        id, tintMaskRef);
            } else {
                tintMaskSha = sha1(mask);
                blobs.put(tintMaskSha, new AttachmentServerData.Blob(mask, AttachmentServerData.KIND_TEXTURE));
            }
        }
        boolean translucent = GsonHelper.getAsString(json, "render", "cutout").equals("translucent");
        String emissiveSha = "";
        String emissiveRef = GsonHelper.getAsString(json, "emissive", "");
        if (!emissiveRef.isEmpty()) {
            ResourceLocation emissiveFile = resolve(ns, emissiveRef, "textures", ".png");
            byte[] emissive = emissiveFile == null ? null : readBytes(manager, emissiveFile, MAX_TEXTURE_BYTES);
            if (emissive == null) {
                Townstead.LOGGER.warn("Attachment {} emissive texture '{}' not found; layer skipped", id, emissiveRef);
            } else {
                emissiveSha = sha1(emissive);
                blobs.put(emissiveSha, new AttachmentServerData.Blob(emissive, AttachmentServerData.KIND_TEXTURE));
            }
        }
        List<AttachmentDef.MorphChannel> morph = parseMorph(json);
        List<AttachmentDef.VisibilityRule> visibility = parseVisibility(json);
        Map<String, AttachmentDef.StageOverride> stages = new LinkedHashMap<>();
        if (json.has("stages") && json.get("stages").isJsonObject()) {
            for (var entry : json.getAsJsonObject("stages").entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject stage = entry.getValue().getAsJsonObject();
                String stageGeoSha = null;
                String geoRef = GsonHelper.getAsString(stage, "geometry", "");
                if (!geoRef.isEmpty()) {
                    byte[] stageGeo = geometryBytes(manager, ns, geoRef);
                    if (stageGeo == null) {
                        Townstead.LOGGER.warn("Attachment {} stage '{}' geometry '{}' not found; stage keeps the base model",
                                id, entry.getKey(), geoRef);
                    } else {
                        stageGeoSha = sha1(stageGeo);
                        blobs.put(stageGeoSha, new AttachmentServerData.Blob(stageGeo, AttachmentServerData.KIND_GEO));
                    }
                }
                stages.put(entry.getKey(), new AttachmentDef.StageOverride(
                        GsonHelper.getAsFloat(stage, "scale", 1f), readVec(stage, "offset"), stageGeoSha));
            }
        }
        String whenJson = json.has("when") && json.get("when").isJsonObject()
                ? json.getAsJsonObject("when").toString() : "";
        return new AttachmentDef(id, geoSha, texSha, targetTag, targetPoint, bone,
                readVec(json, "offset"), readVec(json, "rotation"), scale, tint,
                tintSource, tintBlend, tintStrength, tintMaskSha, emissiveSha, translucent,
                readStrings(json, "hides_under"), whenJson,
                morph, visibility, stages, parsePoses(json), parsePhysics(json),
                parseAnimations(manager, ns, id, json, blobs));
    }

    /**
     * The {@code morph} block: the legacy single-channel shorthand ({@code axes} +
     * {@code bones} at the top level = one anonymous channel), or named
     * {@code channels}, each with {@code bones} plus {@code axes} (scale) and/or
     * {@code rotate} (degrees per unit of the channel value).
     */
    private static List<AttachmentDef.MorphChannel> parseMorph(JsonObject json) {
        if (!json.has("morph") || !json.get("morph").isJsonObject()) return List.of();
        JsonObject morph = json.getAsJsonObject("morph");
        List<AttachmentDef.MorphChannel> out = new ArrayList<>();
        if (morph.has("channels") && morph.get("channels").isJsonObject()) {
            for (var entry : morph.getAsJsonObject("channels").entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject channel = entry.getValue().getAsJsonObject();
                float[] axes = channel.has("axes") ? readVec(channel, "axes") : null;
                float[] rotate = channel.has("rotate") ? readVec(channel, "rotate") : null;
                if (axes == null && rotate == null) axes = new float[]{1f, 1f, 1f};
                out.add(new AttachmentDef.MorphChannel(entry.getKey(),
                        readStrings(channel, "bones"), axes, rotate));
            }
        } else {
            out.add(new AttachmentDef.MorphChannel("", readStrings(morph, "bones"),
                    morph.has("axes") ? readVec(morph, "axes") : new float[]{1f, 1f, 1f}, null));
        }
        return out;
    }

    /** The {@code visibility} list: bones gated on a size channel's value (below/above bounds). */
    private static List<AttachmentDef.VisibilityRule> parseVisibility(JsonObject json) {
        if (!json.has("visibility") || !json.get("visibility").isJsonArray()) return List.of();
        List<AttachmentDef.VisibilityRule> out = new ArrayList<>();
        for (var element : json.getAsJsonArray("visibility")) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            List<String> ruleBones = readStrings(rule, "bones");
            if (ruleBones.isEmpty()) continue;
            out.add(new AttachmentDef.VisibilityRule(ruleBones,
                    GsonHelper.getAsString(rule, "channel", ""),
                    GsonHelper.getAsFloat(rule, "below", Float.NaN),
                    GsonHelper.getAsFloat(rule, "above", Float.NaN)));
        }
        return out;
    }

    /**
     * The {@code animations} object: every key except {@code when}/{@code random_idle}
     * is a named state entry; {@code when} entries carry a pheno {@code if}; {@code
     * random_idle} entries are the weighted idle pool. Each references a Blockbench
     * {@code attachment/animations/<file>.animation.json} as {@code "<file>"} or
     * {@code "<file>#<clip>"}; the file loads as a synced blob.
     */
    private static List<AttachmentDef.AnimationEntry> parseAnimations(ResourceManager manager, String ns,
                                                                      String id, JsonObject json,
                                                                      Map<String, AttachmentServerData.Blob> blobs) {
        if (!json.has("animations") || !json.get("animations").isJsonObject()) return List.of();
        List<AttachmentDef.AnimationEntry> out = new ArrayList<>();
        JsonObject animations = json.getAsJsonObject("animations");
        for (var entry : animations.entrySet()) {
            if (entry.getKey().equals("when") || entry.getKey().equals("random_idle")) continue;
            if (!entry.getValue().isJsonObject()) continue;
            addAnimation(manager, ns, id, entry.getValue().getAsJsonObject(),
                    entry.getKey(), "", false, blobs, out);
        }
        if (animations.has("when") && animations.get("when").isJsonArray()) {
            for (var element : animations.getAsJsonArray("when")) {
                if (!element.isJsonObject()) continue;
                JsonObject when = element.getAsJsonObject();
                if (!when.has("if") || !when.get("if").isJsonObject()) continue;
                addAnimation(manager, ns, id, when, "",
                        when.getAsJsonObject("if").toString(), false, blobs, out);
            }
        }
        if (animations.has("random_idle") && animations.get("random_idle").isJsonArray()) {
            for (var element : animations.getAsJsonArray("random_idle")) {
                if (!element.isJsonObject()) continue;
                addAnimation(manager, ns, id, element.getAsJsonObject(), "", "", true, blobs, out);
            }
        }
        return out;
    }

    private static void addAnimation(ResourceManager manager, String ns, String id, JsonObject entry,
                                     String state, String conditionJson, boolean idle,
                                     Map<String, AttachmentServerData.Blob> blobs,
                                     List<AttachmentDef.AnimationEntry> out) {
        String ref = GsonHelper.getAsString(entry, "animation", "");
        if (ref.isEmpty()) {
            Townstead.LOGGER.warn("Attachment {} animation entry missing an 'animation' reference", id);
            return;
        }
        int hash = ref.indexOf('#');
        String file = hash < 0 ? ref : ref.substring(0, hash);
        String clip = hash < 0 ? "" : ref.substring(hash + 1);
        ResourceLocation animFile = resolve(ns, file, "animations", ".animation.json");
        byte[] bytes = animFile == null || manager.getResource(animFile).isEmpty()
                ? null : readBytes(manager, animFile, MAX_GEO_BYTES);
        if (bytes == null) {
            // A bbmodel's embedded animations serve the same reference.
            ResourceLocation bbFile = resolve(ns, file, "bbmodel", ".bbmodel");
            if (bbFile != null && manager.getResource(bbFile).isPresent()) {
                byte[] bb = readBytes(manager, bbFile, Integer.MAX_VALUE);
                if (bb != null) bytes = BbmodelConverter.animations(bb, baseName(file));
            }
        }
        if (bytes == null) {
            Townstead.LOGGER.warn("Attachment {} animation '{}' not found; entry skipped", id, ref);
            return;
        }
        try {
            String sha = sha1(bytes);
            blobs.put(sha, new AttachmentServerData.Blob(bytes, AttachmentServerData.KIND_ANIMATION));
            out.add(new AttachmentDef.AnimationEntry(state, conditionJson, sha, clip,
                    GsonHelper.getAsFloat(entry, "transition", 3f),
                    GsonHelper.getAsFloat(entry, "weight", 1f),
                    GsonHelper.getAsInt(entry, "cooldown", 200), idle));
        } catch (Exception e) {
            Townstead.LOGGER.error("Failed to hash attachment animation {}", animFile, e);
        }
    }

    /** The {@code physics.chains} list: root-to-tip bone runs with spring parameters. */
    private static List<AttachmentDef.PhysicsChain> parsePhysics(JsonObject json) {
        if (!json.has("physics") || !json.get("physics").isJsonObject()) return List.of();
        JsonObject physics = json.getAsJsonObject("physics");
        if (!physics.has("chains") || !physics.get("chains").isJsonArray()) return List.of();
        List<AttachmentDef.PhysicsChain> out = new ArrayList<>();
        for (var element : physics.getAsJsonArray("chains")) {
            if (!element.isJsonObject()) continue;
            JsonObject chain = element.getAsJsonObject();
            List<String> chainBones = readStrings(chain, "bones");
            if (chainBones.isEmpty()) continue;
            float[] response = {1f, 1f, 1f, 1f};
            if (chain.has("response") && chain.get("response").isJsonObject()) {
                JsonObject r = chain.getAsJsonObject("response");
                response[0] = GsonHelper.getAsFloat(r, "vertical", 1f);
                response[1] = GsonHelper.getAsFloat(r, "forward", 1f);
                response[2] = GsonHelper.getAsFloat(r, "lateral", 1f);
                response[3] = GsonHelper.getAsFloat(r, "turn", 1f);
            }
            out.add(new AttachmentDef.PhysicsChain(chainBones,
                    GsonHelper.getAsFloat(chain, "stiffness", 0.4f),
                    GsonHelper.getAsFloat(chain, "damping", 0.8f),
                    GsonHelper.getAsFloat(chain, "gravity", 0.3f),
                    GsonHelper.getAsFloat(chain, "max_angle", 60f),
                    GsonHelper.getAsFloat(chain, "sway", 0f),
                    GsonHelper.getAsFloat(chain, "follow", 0.55f),
                    GsonHelper.getAsFloat(chain, "droop_angle", 40f),
                    GsonHelper.getAsFloat(chain, "sway_speed", 1f),
                    GsonHelper.getAsFloat(chain, "snap", 1f),
                    response,
                    GsonHelper.getAsInt(chain, "segments", 0),
                    GsonHelper.getAsString(chain, "axis", "auto")));
        }
        return out;
    }

    /**
     * The {@code poses} object: every key except {@code when} is a named state entry
     * ({@code sleeping}, {@code sprinting}, ...); {@code when} is a list of
     * condition-gated entries whose {@code if} object ships to the client verbatim
     * and is evaluated there through the pheno condition registry.
     */
    private static List<AttachmentDef.PoseEntry> parsePoses(JsonObject json) {
        if (!json.has("poses") || !json.get("poses").isJsonObject()) return List.of();
        List<AttachmentDef.PoseEntry> out = new ArrayList<>();
        JsonObject poses = json.getAsJsonObject("poses");
        for (var entry : poses.entrySet()) {
            if (entry.getKey().equals("when") || !entry.getValue().isJsonObject()) continue;
            out.add(poseEntry(entry.getKey(), "", entry.getValue().getAsJsonObject()));
        }
        if (poses.has("when") && poses.get("when").isJsonArray()) {
            for (var element : poses.getAsJsonArray("when")) {
                if (!element.isJsonObject()) continue;
                JsonObject when = element.getAsJsonObject();
                if (!when.has("if") || !when.get("if").isJsonObject()) continue;
                out.add(poseEntry("", when.getAsJsonObject("if").toString(), when));
            }
        }
        return out;
    }

    private static AttachmentDef.PoseEntry poseEntry(String state, String conditionJson, JsonObject entry) {
        float[] rotation = entry.has("rotation") ? readVec(entry, "rotation") : null;
        Map<String, float[]> bones = new LinkedHashMap<>();
        if (entry.has("bones") && entry.get("bones").isJsonObject()) {
            for (var bone : entry.getAsJsonObject("bones").entrySet()) {
                if (!bone.getValue().isJsonObject()) continue;
                bones.put(bone.getKey(), readVec(bone.getValue().getAsJsonObject(), "rotation"));
            }
        }
        return new AttachmentDef.PoseEntry(state, conditionJson, rotation, bones,
                GsonHelper.getAsFloat(entry, "transition", 4f));
    }

    private static List<String> readStrings(JsonObject json, String key) {
        List<String> out = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            for (var element : json.getAsJsonArray(key)) out.add(element.getAsString());
        }
        return out;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    /**
     * Geometry bytes for a reference: {@code attachment/geo/<name>.geo.json} verbatim, else
     * {@code attachment/bbmodel/<name>.bbmodel} converted through {@link BbmodelConverter}.
     * A {@code "<name>#<pose>"} reference bakes the named embedded animation as a static
     * pose over the bbmodel's rest pose (ignored for plain geo files).
     */
    private static byte[] geometryBytes(ResourceManager manager, String ns, String ref) {
        int hash = ref.indexOf('#');
        String name = hash < 0 ? ref : ref.substring(0, hash);
        String pose = hash < 0 ? "" : ref.substring(hash + 1);
        ResourceLocation geoFile = resolve(ns, name, "geo", ".geo.json");
        if (geoFile != null && manager.getResource(geoFile).isPresent()) {
            return readBytes(manager, geoFile, Integer.MAX_VALUE);
        }
        ResourceLocation bbFile = resolve(ns, name, "bbmodel", ".bbmodel");
        if (bbFile != null && manager.getResource(bbFile).isPresent()) {
            byte[] bb = readBytes(manager, bbFile, Integer.MAX_VALUE);
            if (bb != null) return BbmodelConverter.geometry(bb, baseName(name), pose);
        }
        return null;
    }

    private static String baseName(String ref) {
        int colon = ref.indexOf(':');
        String name = colon < 0 ? ref : ref.substring(colon + 1);
        int slash = name.lastIndexOf('/');
        return slash < 0 ? name : name.substring(slash + 1);
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
