package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.attachment.geo.BedrockGeometryLoader;
import com.aetherianartificer.townstead.root.attachment.AttachmentDef;
import com.aetherianartificer.townstead.root.attachment.AttachmentRequestC2SPayload;
import com.aetherianartificer.townstead.root.attachment.AttachmentServerData;
import com.aetherianartificer.townstead.root.attachment.AttachmentPointDef;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client store for datapack-synced attachments. Takes the manifest, pulls any blobs
 * it doesn't already have cached (chunked), then materializes geometry into baked
 * {@link ModelPart}s and textures into registered {@link DynamicTexture}s. The render
 * layer reads definitions and the materialized blobs from here.
 */
public final class AttachmentClient {

    private static final Map<String, AttachmentDef> DEFS = new ConcurrentHashMap<>();
    private static final Map<String, AttachmentPointDef> SLOTS = new ConcurrentHashMap<>();
    private static final Map<String, ModelPart> GEO = new ConcurrentHashMap<>();
    // The attachment-model bake of the same blobs (full cube spec: per-face UV, mirror, cube
    // rotation). GEO keeps the vanilla ModelPart bake for the named-geo rig path.
    private static final Map<String, com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeo>
            ATTACHMENT_GEO = new ConcurrentHashMap<>();
    private static final Map<String, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();
    // Parsed .animation.json blobs: SHA-1 -> clip name -> clip.
    private static final Map<String, Map<String, com.aetherianartificer.townstead.root.attachment.AttachmentAnimation.Clip>>
            ANIMATIONS = new ConcurrentHashMap<>();
    private static final Map<String, Buffer> BUFFERS = new ConcurrentHashMap<>();
    // Named datapack textures: logical id ("ns:textures/...") -> SHA-1, so rig/face textures resolve
    // to a synced DynamicTexture without a resource pack.
    private static final Map<String, String> NAMED = new ConcurrentHashMap<>();
    // Named datapack geometry: logical id ("ns:geo/...") -> SHA-1, so a custom-geometry rig model resolves
    // to a synced baked ModelPart (twin of NAMED).
    private static final Map<String, String> NAMED_GEO = new ConcurrentHashMap<>();

    private AttachmentClient() {}

    private static final class Buffer {
        final int total;
        final int kind;
        final byte[][] parts;
        int received;

        Buffer(int total, int kind) {
            this.total = total;
            this.kind = kind;
            this.parts = new byte[total][];
        }
    }

    public static void onManifest(List<AttachmentDef> defs, List<AttachmentPointDef> slots,
                                  Map<String, String> namedTextures, Map<String, String> namedGeo) {
        DEFS.clear();
        SLOTS.clear();
        NAMED.clear();
        NAMED_GEO.clear();
        AttachmentPoses.onManifest();
        AttachmentPhysics.onManifest();
        AttachmentAnimations.onManifest();
        for (AttachmentDef def : defs) DEFS.put(def.id(), def);
        for (AttachmentPointDef slot : slots) SLOTS.put(slot.id(), slot);
        NAMED.putAll(namedTextures);
        NAMED_GEO.putAll(namedGeo);

        Map<String, Integer> needed = new LinkedHashMap<>();
        for (AttachmentDef def : defs) {
            needed.putIfAbsent(def.geoSha1(), AttachmentServerData.KIND_GEO);
            needed.putIfAbsent(def.textureSha1(), AttachmentServerData.KIND_TEXTURE);
            for (AttachmentDef.StageOverride stage : def.stages().values()) {
                if (stage.geoSha1() != null) needed.putIfAbsent(stage.geoSha1(), AttachmentServerData.KIND_GEO);
            }
            for (AttachmentDef.AnimationEntry anim : def.animations()) {
                needed.putIfAbsent(anim.animSha(), AttachmentServerData.KIND_ANIMATION);
            }
            if (!def.emissiveSha1().isEmpty()) {
                needed.putIfAbsent(def.emissiveSha1(), AttachmentServerData.KIND_TEXTURE);
            }
            if (!def.tintMaskSha1().isEmpty()) {
                needed.putIfAbsent(def.tintMaskSha1(), AttachmentServerData.KIND_TEXTURE);
            }
        }
        for (String sha1 : namedTextures.values()) {
            needed.putIfAbsent(sha1, AttachmentServerData.KIND_TEXTURE);
        }
        for (String sha1 : namedGeo.values()) {
            needed.putIfAbsent(sha1, AttachmentServerData.KIND_GEO);
        }

        List<String> request = new ArrayList<>();
        needed.forEach((sha1, kind) -> {
            if (GEO.containsKey(sha1) || TEXTURES.containsKey(sha1) || ANIMATIONS.containsKey(sha1)) return;
            byte[] cached = AttachmentCache.read(sha1);
            if (cached != null) materialize(sha1, kind, cached);
            else request.add(sha1);
        });
        if (!request.isEmpty()) sendRequest(request);
    }

    public static void onChunk(String sha1, int index, int total, int kind, byte[] data) {
        if (GEO.containsKey(sha1) || TEXTURES.containsKey(sha1) || ANIMATIONS.containsKey(sha1)) return;
        Buffer buffer = BUFFERS.computeIfAbsent(sha1, k -> new Buffer(total, kind));
        if (index < 0 || index >= buffer.parts.length || buffer.parts[index] != null) return;
        buffer.parts[index] = data;
        buffer.received++;
        if (buffer.received < buffer.total) return;

        BUFFERS.remove(sha1);
        int size = 0;
        for (byte[] part : buffer.parts) size += part.length;
        byte[] bytes = new byte[size];
        int pos = 0;
        for (byte[] part : buffer.parts) {
            System.arraycopy(part, 0, bytes, pos, part.length);
            pos += part.length;
        }
        AttachmentCache.write(sha1, bytes);
        materialize(sha1, buffer.kind, bytes);
    }

    private static void materialize(String sha1, int kind, byte[] bytes) {
        try {
            if (kind == AttachmentServerData.KIND_GEO) {
                var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                // The ModelPart bake feeds full-body rigs (namedGeo), so it takes the ground-origin
                // shift: Blockbench entity/avatar projects put the ground at y=0, Java at y=24.
                ModelPart part = BedrockGeometryLoader.parse(json, true);
                if (part != null) GEO.put(sha1, part);
                var geo = com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeoLoader.parse(json);
                if (geo != null) ATTACHMENT_GEO.put(sha1, geo);
            } else if (kind == AttachmentServerData.KIND_ANIMATION) {
                var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                List<String> warnings = new ArrayList<>();
                var clips = com.aetherianartificer.townstead.root.attachment.AttachmentAnimation.parse(json, warnings);
                for (String warning : warnings) Townstead.LOGGER.warn("Attachment animation {}: {}", sha1, warning);
                ANIMATIONS.put(sha1, clips);
            } else {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                ResourceLocation id = ResourceLocation.tryParse(Townstead.MOD_ID + ":attachment/" + sha1);
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
                TEXTURES.put(sha1, id);
            }
        } catch (Exception e) {
            Townstead.LOGGER.error("Failed to materialize attachment blob {}", sha1, e);
        }
    }

    public static AttachmentDef def(String id) {
        return DEFS.get(id);
    }

    public static AttachmentPointDef slot(String id) {
        return SLOTS.get(id);
    }

    public static ModelPart geometry(String sha1) {
        return GEO.get(sha1);
    }

    /** The attachment-model bake (per-face UV, mirror, named bones) of a synced geo blob. */
    public static com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeo attachmentGeometry(String sha1) {
        return ATTACHMENT_GEO.get(sha1);
    }

    // Mirrored re-bakes for mirror attachment points, lazy per blob (content-addressed, so never stale).
    private static final Map<String, com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeo>
            MIRRORED_GEO = new ConcurrentHashMap<>();

    /** The mirrored bake of a synced geo blob, or null while the base hasn't materialized. */
    public static com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeo mirroredGeometry(String sha1) {
        var base = ATTACHMENT_GEO.get(sha1);
        if (base == null) return null;
        return MIRRORED_GEO.computeIfAbsent(sha1, k -> base.mirrored());
    }

    // Segmented (and segmented+mirrored) re-bakes for physics chains with `segments`:
    // the geometry JSON is re-parsed with the named bone sliced into chained sub-bones.
    // Keyed by blob + slicing spec, so defs sharing a blob with different chains coexist.
    private static final Map<String, com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeo>
            SEGMENTED_GEO = new ConcurrentHashMap<>();
    private static final Map<String, com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeo>
            SEGMENTED_MIRRORED_GEO = new ConcurrentHashMap<>();

    /**
     * The bake the render layer should draw for this definition: segmented when any
     * physics chain asks for it, mirrored when the anchor is a mirror point, plain
     * otherwise. Null while the blob hasn't materialized.
     */
    public static com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeo geometryFor(
            AttachmentDef def, String sha1, boolean mirror) {
        String spec = segmentSpec(def);
        if (spec.isEmpty()) return mirror ? mirroredGeometry(sha1) : attachmentGeometry(sha1);
        String key = sha1 + "|" + spec;
        var segmented = SEGMENTED_GEO.get(key);
        if (segmented == null) {
            if (ATTACHMENT_GEO.get(sha1) == null) return null;   // blob not materialized yet
            byte[] bytes = AttachmentCache.read(sha1);
            if (bytes == null) return mirror ? mirroredGeometry(sha1) : attachmentGeometry(sha1);
            try {
                var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                for (AttachmentDef.PhysicsChain chain : def.physics()) {
                    if (chain.segments() > 1 && chain.bones().size() == 1) {
                        json = com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeoSegmenter
                                .segment(json, chain.bones().get(0), chain.segments(), chain.axis());
                    }
                }
                segmented = com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeoLoader.parse(json);
            } catch (Exception e) {
                Townstead.LOGGER.error("Failed to bake segmented geometry for {}", def.id(), e);
            }
            if (segmented == null) return mirror ? mirroredGeometry(sha1) : attachmentGeometry(sha1);
            SEGMENTED_GEO.put(key, segmented);
        }
        if (!mirror) return segmented;
        var base = segmented;
        return SEGMENTED_MIRRORED_GEO.computeIfAbsent(key, k -> base.mirrored());
    }

    /** The def's slicing spec ("bone:count:axis;..."), empty when no chain segments. */
    private static String segmentSpec(AttachmentDef def) {
        StringBuilder spec = null;
        for (AttachmentDef.PhysicsChain chain : def.physics()) {
            if (chain.segments() > 1 && chain.bones().size() == 1) {
                if (spec == null) spec = new StringBuilder();
                else spec.append(';');
                spec.append(chain.bones().get(0)).append(':').append(chain.segments())
                        .append(':').append(chain.axis());
            }
        }
        return spec == null ? "" : spec.toString();
    }

    /** Every synced attachment point (rig filtering happens at the render layer). */
    public static List<AttachmentPointDef> allPoints() {
        return new ArrayList<>(SLOTS.values());
    }

    public static ResourceLocation texture(String sha1) {
        return TEXTURES.get(sha1);
    }

    // Derived textures baked with a SkinBlend-packed tint (screen/overlay/color modes and
    // faded tints, which a flat vertex multiply can't express), keyed sha1 + mask + packed tint.
    // Keys are deterministic, so a cache clear only costs a re-bake, never a texture leak.
    private static final Map<String, ResourceLocation> BLENDED = new ConcurrentHashMap<>();

    /**
     * The texture blob re-baked through {@link com.aetherianartificer.townstead.client.skin.SkinBlend}
     * with a packed tint (render-thread, lazy, cached). Falls back to the plain texture when
     * the blob bytes aren't cached yet.
     *
     * <p>{@code maskSha1} is an optional grayscale tint mask: each pixel's luminance is the
     * blend's per-pixel strength, so black keeps the base colour, white takes the full tint,
     * and mid-greys ease between them (a tinted membrane against untinted chitin, soft edges
     * for free). Fully transparent mask pixels read as black. A mask whose dimensions differ
     * from the base is ignored rather than sampled wrong — {@code AttachmentDoctor} reports
     * the mismatch. While a declared mask's bytes are still syncing the plain texture is
     * returned uncached, so the attachment draws untinted for those frames instead of
     * flashing a fully tinted body.</p>
     */
    public static ResourceLocation blendedTexture(String sha1, String maskSha1, int packed) {
        String key = sha1 + "#" + maskSha1 + "#" + Integer.toHexString(packed);
        ResourceLocation cached = BLENDED.get(key);
        if (cached != null) return cached;
        byte[] bytes = AttachmentCache.read(sha1);
        if (bytes == null) return TEXTURES.get(sha1);
        byte[] maskBytes = null;
        if (!maskSha1.isEmpty()) {
            maskBytes = AttachmentCache.read(maskSha1);
            // Declared but not yet synced: stay untinted for now and retry next frame.
            if (maskBytes == null) return TEXTURES.get(sha1);
        }
        // Declared outside the try so the finally releases it even if the bake throws. The
        // baked image itself is handed to the DynamicTexture and owned by the texture manager.
        NativeImage mask = null;
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            if (maskBytes != null) mask = NativeImage.read(new ByteArrayInputStream(maskBytes));
            if (mask != null && (mask.getWidth() != image.getWidth() || mask.getHeight() != image.getHeight())) {
                Townstead.LOGGER.warn("Attachment tint mask {} is {}x{} but the texture is {}x{}; mask ignored",
                        maskSha1, mask.getWidth(), mask.getHeight(), image.getWidth(), image.getHeight());
                mask.close();
                mask = null;
            }
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    int rgb = ((abgr & 0xFF) << 16) | (abgr & 0xFF00) | ((abgr >> 16) & 0xFF);
                    float gate = mask == null ? 1f : maskGate(mask.getPixelRGBA(x, y));
                    int out = rgb;
                    if (gate > 0f) {
                        int blended = com.aetherianartificer.townstead.client.skin.SkinBlend.blend(rgb, packed);
                        out = gate >= 1f ? blended
                                : com.aetherianartificer.townstead.client.skin.SkinBlend.lerpRgb(rgb, blended, gate);
                    }
                    image.setPixelRGBA(x, y, (abgr & 0xFF000000)
                            | ((out & 0xFF) << 16) | (out & 0xFF00) | ((out >> 16) & 0xFF));
                }
            }
            if (BLENDED.size() > 512) BLENDED.clear();
            ResourceLocation id = ResourceLocation.tryParse(
                    Townstead.MOD_ID + ":attachment/" + sha1 + "/t" + Integer.toHexString(packed)
                            + (maskSha1.isEmpty() ? "" : "m" + maskSha1));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            BLENDED.put(key, id);
            return id;
        } catch (Exception e) {
            Townstead.LOGGER.error("Failed to bake blended attachment texture {}", sha1, e);
            BLENDED.put(key, TEXTURES.getOrDefault(sha1, ResourceLocation.tryParse(
                    Townstead.MOD_ID + ":attachment/" + sha1)));
            return TEXTURES.get(sha1);
        } finally {
            if (mask != null) mask.close();
        }
    }

    /**
     * A tint mask pixel's 0..1 blend strength: its luminance, with fully transparent pixels
     * reading as black (untinted) so an author can cut the mask out instead of painting it
     * black. {@link NativeImage} packs pixels ABGR, hence the channel order.
     */
    private static float maskGate(int abgr) {
        if (((abgr >>> 24) & 0xFF) == 0) return 0f;
        int r = abgr & 0xFF;
        int g = (abgr >> 8) & 0xFF;
        int b = (abgr >> 16) & 0xFF;
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
    }

    /**
     * A clip from a synced {@code .animation.json} blob: the named clip, the file's
     * only clip when {@code name} is empty, or a clip whose Blockbench-prefixed name
     * ends in {@code .<name>}. Null while the blob hasn't materialized or nothing matches.
     */
    public static com.aetherianartificer.townstead.root.attachment.AttachmentAnimation.Clip animationClip(
            String sha1, String name) {
        var clips = ANIMATIONS.get(sha1);
        if (clips == null || clips.isEmpty()) return null;
        if (name.isEmpty()) return clips.values().iterator().next();
        var exact = clips.get(name);
        if (exact != null) return exact;
        for (var entry : clips.entrySet()) {
            if (entry.getKey().endsWith("." + name)) return entry.getValue();
        }
        return null;
    }

    /**
     * Resolve a named datapack texture ("ns:textures/...") to its synced {@link DynamicTexture}
     * location, or {@code null} if it isn't a datapack texture or hasn't materialized yet (caller
     * falls back to a plain ResourceLocation for vanilla/resource-pack textures).
     */
    public static ResourceLocation namedTexture(String id) {
        String sha1 = NAMED.get(id);
        return sha1 == null ? null : TEXTURES.get(sha1);
    }

    /**
     * The raw PNG bytes behind a named datapack texture, for callers that need its PIXELS rather
     * than a bound texture (the eye-strip compositor). Null when it isn't a datapack texture or its
     * blob hasn't arrived yet.
     */
    public static byte[] namedTextureBytes(String id) {
        String sha1 = NAMED.get(id);
        return sha1 == null ? null : AttachmentCache.read(sha1);
    }

    /**
     * Resolve a named datapack geometry ("ns:geo/...") to its synced baked {@link ModelPart}, or
     * {@code null} if it isn't a datapack geo or hasn't materialized yet (the caller leaves the rig
     * un-cached so it retries once the blob arrives).
     */
    public static ModelPart namedGeo(String id) {
        String sha1 = NAMED_GEO.get(id);
        return sha1 == null ? null : GEO.get(sha1);
    }

    public static void clear() {
        DEFS.clear();
        SLOTS.clear();
        NAMED.clear();
        NAMED_GEO.clear();
        BUFFERS.clear();
        // Baked geometry and registered textures are kept: they're content-addressed
        // and reused if the same blobs appear again next session.
    }

    private static void sendRequest(List<String> hashes) {
        AttachmentRequestC2SPayload payload = new AttachmentRequestC2SPayload(hashes);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }
}
