package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.attachment.AttachmentClient;
import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import com.aetherianartificer.townstead.root.gene.AllelePayload;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A humanoid-body root's own eyes, in place of MCA's. Where a custom rig draws its face as overlay
 * quads ({@link SpeciesFace}), a humanoid bearer keeps MCA's model and face shell: the bearer's
 * {@code eyes} gene supplies an eye strip, the wanted frame is composited into the head front of a
 * blank skin-format canvas, and that canvas is drawn on MCA's own face model — so the eyes wrap the
 * head exactly like MCA's do, through head scaling, proportions, and sleep poses. MCA's
 * {@code FaceLayer} (its eyes, blink, and iris tinting) is suppressed for that bearer by
 * {@code FaceLayerCustomEyesMixin}, which calls {@link #render}.
 *
 * <p>Texture contract: a horizontal strip of 8-wide frames in the order {@code [open, blink, happy,
 * unhappy]}, up to 8 rows tall, transparent where the face shows through. The strip lands on the
 * head-front UV column (x 8..15) at the gene's {@code row}, defaulting to bottom-aligned on row 14
 * where vanilla eyes sit. A one-frame set still blinks: the blink is derived from the art by taking
 * each column's darkest pixel and collapsing it to the baseline row, the same read as MCA's blink
 * line. A source at least 32 rows tall is taken as full skin-format frames instead (64 wide each),
 * used as authored.</p>
 */
public final class HumanoidEyes {

    private HumanoidEyes() {}

    // Head-front UV square of a skin-format texture, and the row vanilla eyes rest on.
    private static final int FACE_X = 8;
    private static final int FACE_TOP = 8;
    private static final int FACE_SIZE = 8;
    private static final int BASELINE_ROW = 14;
    private static final int CANVAS = 64;
    // A source this tall is a whole skin canvas rather than an eye band.
    private static final int FULL_SKIN_MIN_HEIGHT = 32;

    /** One bearer's resolved eye set. */
    private record Eyes(String texture, boolean glow, int row, String tint) {}

    // Composited frames, keyed texture + row + frame. Deterministic keys, so a clear only costs a
    // re-bake; capped because every (set, frame) pair holds a registered texture.
    private static final Map<String, ResourceLocation> BAKED = new ConcurrentHashMap<>();

    /**
     * Draws the bearer's own eyes onto MCA's face model, returning true when it did — the caller
     * then suppresses MCA's. False means this bearer has no custom eye set (or its texture hasn't
     * synced yet), and MCA's own eyes should render.
     */
    public static boolean render(HumanoidModel<?> model, PoseStack transform, MultiBufferSource provider,
                                 int light, LivingEntity entity, boolean visible, boolean glowing) {
        Eyes eyes = resolve(entity);
        if (eyes == null) return false;
        ResourceLocation texture = frameTexture(eyes.texture(), eyes.row(), FaceExpression.eyeFrame(entity));
        if (texture == null) return false;   // blob still syncing: MCA's eyes stand in for a frame

        RenderType layer;
        if (!visible) {
            layer = glowing ? RenderType.outline(texture) : null;
        } else {
            layer = eyes.glow() ? RenderType.eyes(texture) : RenderType.itemEntityTranslucentCull(texture);
        }
        if (layer == null) return true;   // nothing to draw, but MCA's eyes stay replaced

        int color = tint(entity, eyes.tint());
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);
        VertexConsumer buffer = provider.getBuffer(layer);
        //? if neoforge {
        model.renderToBuffer(transform, buffer, light, overlay, color);
        //?} else {
        /*model.renderToBuffer(transform, buffer, light, overlay,
                ((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f,
                ((color >>> 24) & 0xFF) / 255f);
        *///?}
        return true;
    }

    /**
     * The bearer's eye set: the expressed (else granted) eyes gene, with the carried variant's own
     * texture when the set has options. Null for anyone without one, and for custom-rig bearers —
     * a rig draws its whole face itself, on its own geometry.
     */
    private static Eyes resolve(LivingEntity entity) {
        if (entity == null) return null;
        if (RigModels.isAlternate(RigModels.rigBaseFor(entity))) return null;
        for (String geneId : geneIds(entity)) {
            GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
            if (gene == null || !gene.isEyes()) continue;
            String texture = gene.eyesTexture();
            boolean glow = gene.eyesGlow();
            if (!gene.variants().isEmpty()) {
                String carried = AllelePayload.parse(
                        RootClientStore.resolveCarriedVariant(entity, gene.id())).variant();
                GeneCatalogEntry.Variant option = variant(gene, carried);
                if (option != null && !option.texture().isEmpty()) {
                    texture = option.texture();
                    glow = option.glow();
                }
            }
            if (texture.isEmpty()) continue;
            return new Eyes(texture, glow, gene.eyesRow(), gene.eyesTint());
        }
        return null;
    }

    /** The carried option, else the first one that carries a texture (an unsynced carry). */
    private static GeneCatalogEntry.Variant variant(GeneCatalogEntry gene, String carried) {
        for (GeneCatalogEntry.Variant option : gene.variants()) {
            if (option.id().equals(carried)) return option;
        }
        for (GeneCatalogEntry.Variant option : gene.variants()) {
            if (!option.texture().isEmpty()) return option;
        }
        return null;
    }

    /** The gene ids the bearer could wear: its expressed set, else its root's grant list. */
    private static Set<String> geneIds(LivingEntity entity) {
        Set<String> expressed = RootClientStore.expressedGenes(entity);
        if (!expressed.isEmpty()) return expressed;
        String rootId = RootClientStore.resolve(entity);
        if (rootId.isEmpty()) return Set.of();
        RootCatalogEntry root = RootCatalogClient.origin(rootId);
        if (root == null) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (RootCatalogEntry.Inherited inherited : root.inheritedGenes()) out.add(inherited.geneId());
        return out;
    }

    /**
     * The colour multiplied over the strip: the texture's own colours by default, the bearer's
     * carried {@code eye_color} variant for {@code "eye_color"} (greyscale art that inherits its
     * colour, the same gene the rig faces tint from), or a flat hex tint.
     */
    private static int tint(LivingEntity entity, String spec) {
        if (spec == null || spec.isEmpty()) return 0xFFFFFFFF;
        if (spec.equals("eye_color")) {
            int color = RigEyeColor.forEntity(entity);
            return color >= 0 ? 0xFF000000 | color : 0xFFFFFFFF;
        }
        try {
            return 0xFF000000 | (Integer.parseInt(spec.startsWith("#") ? spec.substring(1) : spec, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return 0xFFFFFFFF;
        }
    }

    /**
     * A skin-format texture carrying one frame of an eye strip, baked once and cached. Null while
     * the source hasn't arrived (a datapack blob mid-sync) or can't be read.
     */
    private static ResourceLocation frameTexture(String textureId, int row, int frame) {
        String key = textureId + "#" + row + "#" + frame;
        ResourceLocation cached = BAKED.get(key);
        if (cached != null) return cached;
        NativeImage source = read(textureId);
        if (source == null) return null;
        try {
            NativeImage baked = bake(source, row, frame);
            if (baked == null) return null;
            if (BAKED.size() > 256) BAKED.clear();
            ResourceLocation id = ResourceLocation.tryParse(
                    Townstead.MOD_ID + ":eyes/" + sanitize(textureId) + "/r" + row + "/f" + frame);
            if (id == null) {
                baked.close();
                return null;
            }
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(baked));
            BAKED.put(key, id);
            return id;
        } catch (Exception e) {
            Townstead.LOGGER.error("Failed to bake eye frame {} of {}", frame, textureId, e);
            return null;
        } finally {
            source.close();
        }
    }

    /**
     * One strip frame composited onto a blank skin canvas at the head front. A frame the strip
     * doesn't have falls back to {@code open}, except the blink of a single-frame set, which is
     * derived from the art: each column's darkest pixel, collapsed onto the baseline row.
     */
    private static NativeImage bake(NativeImage source, int row, int frame) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0) return null;

        // A full skin canvas: frames are whole 64-wide textures, used as authored.
        if (height >= FULL_SKIN_MIN_HEIGHT) {
            int frameWidth = width % CANVAS == 0 ? CANVAS : width;
            int frames = Math.max(1, width / frameWidth);
            int f = frame < frames ? frame : 0;
            NativeImage out = new NativeImage(frameWidth, height, true);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < frameWidth; x++) {
                    out.setPixelRGBA(x, y, source.getPixelRGBA(f * frameWidth + x, y));
                }
            }
            return out;
        }

        int frameWidth = width % FACE_SIZE == 0 ? FACE_SIZE : width;
        int frames = Math.max(1, width / frameWidth);
        int bandWidth = Math.min(frameWidth, FACE_SIZE);
        int bandHeight = Math.min(height, FACE_SIZE);
        // Bottom-aligned on the vanilla eye baseline unless the set names its own row.
        int top = row >= 0 ? row : BASELINE_ROW - (bandHeight - 1);
        top = Math.max(FACE_TOP, Math.min(FACE_TOP + FACE_SIZE - bandHeight, top));
        int left = FACE_X + (FACE_SIZE - bandWidth) / 2;

        boolean derive = frame == FaceExpression.EYES_BLINK && frames < 2;
        int f = frame < frames ? frame : 0;

        NativeImage out = new NativeImage(CANVAS, CANVAS, true);
        if (derive) {
            for (int x = 0; x < bandWidth; x++) {
                int shut = darkest(source, f * frameWidth + x, bandHeight);
                if (shut != 0) out.setPixelRGBA(left + x, top + bandHeight - 1, shut);
            }
            return out;
        }
        for (int y = 0; y < bandHeight; y++) {
            for (int x = 0; x < bandWidth; x++) {
                out.setPixelRGBA(left + x, top + y, source.getPixelRGBA(f * frameWidth + x, y));
            }
        }
        return out;
    }

    /**
     * The darkest opaque pixel of a source column (ABGR, as {@link NativeImage} packs them), or 0
     * when the column is empty — the shut-eye line of a derived blink.
     */
    private static int darkest(NativeImage source, int x, int height) {
        int best = 0;
        int bestLuma = Integer.MAX_VALUE;
        for (int y = 0; y < height; y++) {
            int abgr = source.getPixelRGBA(x, y);
            if (((abgr >>> 24) & 0xFF) == 0) continue;
            int r = abgr & 0xFF;
            int g = (abgr >> 8) & 0xFF;
            int b = (abgr >> 16) & 0xFF;
            int luma = 299 * r + 587 * g + 114 * b;
            if (luma < bestLuma) {
                bestLuma = luma;
                best = 0xFF000000 | (abgr & 0xFFFFFF);
            }
        }
        return best;
    }

    /** The strip's pixels: a synced datapack blob, else a mod-asset / resource-pack texture. */
    private static NativeImage read(String textureId) {
        try {
            byte[] bytes = AttachmentClient.namedTextureBytes(textureId);
            if (bytes != null) return NativeImage.read(new ByteArrayInputStream(bytes));
            ResourceLocation id = DataPackLang.parseId(textureId);
            if (id == null) return null;
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) return null;
            try (InputStream stream = resource.get().open()) {
                return NativeImage.read(stream);
            }
        } catch (Exception e) {
            Townstead.LOGGER.error("Failed to read eye strip {}", textureId, e);
            return null;
        }
    }

    /** A texture id folded into a resource-location-safe path segment. */
    private static String sanitize(String id) {
        StringBuilder out = new StringBuilder(id.length());
        for (char c : id.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' ? c : '_');
        }
        return out.toString();
    }
}
