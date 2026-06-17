package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.origin.OriginCatalogClient;
import com.aetherianartificer.townstead.client.origin.OriginClientStore;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.GeneCatalogEntry;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Draws a custom-faced rig's eyes + mouth onto the (faceless) head front, so individuals are
 * recognizable and a little alive. Rendered from inside {@link SpeciesRigLayer} after the body, using
 * the rig's already-posed head bone, so it follows the head with no extra pose pass.
 *
 * <p>Identity is fixed per villager (the carried {@code eyes}/{@code mouth} variant); the EXPRESSION
 * frame is chosen each render from a UUID-phased blink and the MCA mood (smile/frown). The eye SHAPE
 * texture is greyscale and tinted by the carried {@code eye_color} variant; an eye set flagged
 * {@code glow} draws full-bright via {@link RenderType#eyes}.</p>
 *
 * <p>Texture contract: each set texture is a horizontal sprite strip, one square frame per expression
 * in a fixed order — eyes {@code [open, blink, happy, unhappy]} (4), mouth {@code [neutral, happy,
 * unhappy]} (3). Each frame is the full head-front 8×8 region (feature drawn in place, transparent
 * elsewhere).</p>
 */
public final class SpeciesFace {

    private SpeciesFace() {}

    private static final int EYES_FRAMES = 4;
    private static final int MOUTH_FRAMES = 3;
    // How far in front of the face plane each overlay sits (eyes slightly ahead of mouth), to avoid
    // z-fighting with the head surface. Pushed along the face's forward direction.
    private static final float EYES_EPS = 0.011f;
    private static final float MOUTH_EPS = 0.010f;

    static void render(LivingEntity entity, String rigBase, PoseStack pose, MultiBufferSource buffers,
                       int light, float partialTick) {
        // Face placement is data-driven per rig (no humanoid-head assumption): a rig with no `face`
        // block has no overlay face.
        com.aetherianartificer.townstead.origin.rig.RigDefinition def = RigModels.definition(rigBase);
        if (def == null || def.face() == null) return;
        com.aetherianartificer.townstead.origin.rig.RigDefinition.Face face = def.face();
        ModelPart head = RigModels.bone(rigBase, face.bone());
        if (head == null) return;

        String originId = OriginClientStore.resolve(entity);
        OriginCatalogEntry origin = OriginCatalogClient.origin(originId);
        if (origin == null) return;
        GeneCatalogEntry eyesGene = null, mouthGene = null, colorGene = null;
        for (OriginCatalogEntry.Inherited inh : origin.inheritedGenes()) {
            GeneCatalogEntry g = OriginCatalogClient.gene(inh.geneId());
            if (g == null) continue;
            if (g.isEyes()) eyesGene = g;
            else if (g.isMouth()) mouthGene = g;
            else if (g.isEyeColor()) colorGene = g;
        }
        if (eyesGene == null && mouthGene == null) return;

        GeneCatalogEntry.Variant eyes = variantOf(entity, eyesGene);
        GeneCatalogEntry.Variant mouth = variantOf(entity, mouthGene);
        // One face colour tints BOTH eyes and mouth, so they always match. White (untinted) only when
        // the origin has no eye_color gene.
        int faceTint = 0xFFFFFFFF;
        GeneCatalogEntry.Variant color = variantOf(entity, colorGene);
        if (color != null && color.tint() >= 0) faceTint = 0xFF000000 | color.tint();

        // Eyes are closed (the blink frame) while asleep, else a periodic blink.
        boolean closed = entity.isSleeping() || blinking(entity);
        // Mood expression (smile/frown, happy/sad eyes) only flashes for a moment when the mood CHANGES,
        // then relaxes to neutral — no permanent grin.
        int reaction = reactionSign(entity);

        pose.pushPose();
        head.translateAndRotate(pose);
        if (eyes != null && !eyes.texture().isEmpty()) {
            int frame = closed ? 1 : (reaction > 0 ? 2 : reaction < 0 ? 3 : 0);
            ResourceLocation tex = resolveTexture(eyes.texture());
            if (tex != null) {
                RenderType type = eyes.glow() ? RenderType.eyes(tex) : RenderType.entityCutoutNoCull(tex);
                quad(buffers.getBuffer(type), pose, face, EYES_EPS, EYES_FRAMES, frame, faceTint, light);
            }
        }
        if (mouth != null && !mouth.texture().isEmpty()) {
            int frame = reaction > 0 ? 1 : reaction < 0 ? 2 : 0;
            ResourceLocation tex = resolveTexture(mouth.texture());
            if (tex != null) {
                quad(buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), pose, face, MOUTH_EPS, MOUTH_FRAMES,
                        frame, faceTint, light);
            }
        }
        pose.popPose();
    }

    /** A face texture id to its synced DynamicTexture (no resource pack), else a plain resource location. */
    private static ResourceLocation resolveTexture(String id) {
        ResourceLocation synced = com.aetherianartificer.townstead.client.attachment.AttachmentClient.namedTexture(id);
        return synced != null ? synced : DataPackLang.parseId(id);
    }

    /** The entity's carried variant for a face gene, else a stable UUID pick so it varies before sync. */
    private static GeneCatalogEntry.Variant variantOf(LivingEntity entity, GeneCatalogEntry gene) {
        if (gene == null || gene.variants().isEmpty()) return null;
        String rolled = OriginClientStore.resolveCarriedVariant(entity, gene.id());
        if (rolled != null && !rolled.isEmpty()) {
            for (GeneCatalogEntry.Variant v : gene.variants()) {
                if (v.id().equals(rolled)) return v;
            }
        }
        return gene.variants().get(Math.floorMod(entity.getUUID().hashCode(), gene.variants().size()));
    }

    // Mood reacts to CHANGE, not the standing value: a smile/frown only flashes when the villager's
    // mood shifts (got a gift, a good trade, took a hit...), then relaxes to neutral — no permanent
    // grin. Per-entity last-seen mood + when the reaction ends, in the entity's own tick clock.
    private record Reaction(int lastMood, long untilTick, int sign) {}
    private static final java.util.Map<Integer, Reaction> REACTIONS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int REACTION_TICKS = 50;   // ~2.5s of expression after a mood change

    /** +1 smile / -1 frown / 0 neutral — non-zero only during the brief window after a mood change. */
    private static int reactionSign(LivingEntity entity) {
        if (!(entity instanceof VillagerEntityMCA villager)) return 0;
        int mood;
        try {
            mood = villager.getVillagerBrain().getMoodValue();
        } catch (Throwable t) {
            return 0;
        }
        long now = entity.tickCount;
        Reaction r = REACTIONS.get(entity.getId());
        if (r == null) {
            REACTIONS.put(entity.getId(), new Reaction(mood, 0, 0));   // first sight: no reaction
            return 0;
        }
        if (mood != r.lastMood()) {   // mood just shifted: start a reaction in its direction
            r = new Reaction(mood, now + REACTION_TICKS, mood > r.lastMood() ? 1 : -1);
            REACTIONS.put(entity.getId(), r);
        }
        return now < r.untilTick() ? r.sign() : 0;
    }

    /** A brief, per-entity-phased blink so a crowd doesn't blink in unison. */
    private static boolean blinking(LivingEntity entity) {
        long phase = entity.getUUID().getLeastSignificantBits();
        int period = 70 + (int) Math.floorMod(phase, 71);   // 70..140 ticks
        return Math.floorMod(entity.tickCount + Math.floorMod(phase, period), period) < 3;
    }

    /**
     * A quad on the rig's face plane (from its {@link RigDefinition.Face} center/size, in model
     * pixels), pushed {@code eps} along the face's forward direction, textured with frame {@code f}.
     * Forward {@code -1} faces the bone's -Z (a vanilla humanoid head front); {@code +1} faces +Z.
     */
    private static void quad(VertexConsumer vc, PoseStack pose,
                             com.aetherianartificer.townstead.origin.rig.RigDefinition.Face face,
                             float eps, int frames, int f, int color, int light) {
        float fwd = face.forward() >= 0 ? 1f : -1f;
        float cx = face.center()[0] / 16f, cy = face.center()[1] / 16f;
        float z = face.center()[2] / 16f + fwd * eps;
        float hw = face.size()[0] / 32f, hh = face.size()[1] / 32f;   // half width/height (px/16/2)
        float u0 = (float) f / frames, u1 = (float) (f + 1) / frames;
        PoseStack.Pose ms = pose.last();
        // Winding/u flip with forward so the textured side faces outward whichever way the head points.
        float ul = fwd < 0 ? u0 : u1, ur = fwd < 0 ? u1 : u0;
        vert(vc, ms, cx - hw, cy + hh, z, ul, 1f, color, light, fwd);
        vert(vc, ms, cx + hw, cy + hh, z, ur, 1f, color, light, fwd);
        vert(vc, ms, cx + hw, cy - hh, z, ur, 0f, color, light, fwd);
        vert(vc, ms, cx - hw, cy - hh, z, ul, 0f, color, light, fwd);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose ms, float x, float y, float z,
                             float u, float v, int color, int light, float nz) {
        int a = (color >>> 24) & 0xFF, r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        //? if >=1.21 {
        vc.addVertex(ms, x, y, z).setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms, 0f, 0f, nz);
        //?} else {
        /*vc.vertex(ms.pose(), x, y, z).color(r, g, b, a).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(ms.normal(), 0f, 0f, nz).endVertex();
        *///?}
    }
}
