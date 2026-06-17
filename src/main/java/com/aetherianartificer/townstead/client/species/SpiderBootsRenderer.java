package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.origin.rig.RigDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays worn boots across a non-humanoid rig's legs (e.g. one per spider leg). Vanilla draws only two
 * boots; here we drive vanilla's own per-slot armor render ({@link ArmorLayerBootsMixin}) once per
 * leg-pair, with a throwaway humanoid pose model whose two legs are positioned onto a pair of the
 * rig's actual leg bones (read post-{@code setupAnim}, so they track the walk). Because we reuse
 * {@code renderArmorPiece} verbatim, every material's texture, dye, trim, and enchant glint comes for
 * free; we only supply where each boot sits. The host's own FEET draw is suppressed for these entities
 * by the same mixin, so a player's boots are not also drawn at the humanoid legs.
 */
public final class SpiderBootsRenderer {

    private static final ThreadLocal<Boolean> RENDERING = ThreadLocal.withInitial(() -> false);
    private static final float INNER_DEFORM = 0.5f;
    private static final float OUTER_DEFORM = 1.0f;

    // Vanilla's private per-slot armor render, resolved by reflection (official name on NeoForge, SRG
    // on Forge). Reused verbatim so dye/trim/glint/material handling all come for free; a mixin
    // @Invoker can't bind it cross-version on the refmap-less 1.20.1 build.
    private static java.lang.reflect.Method renderPiece;
    private static boolean renderPieceResolved;

    // Per rig: the armor layer (its parent is the pose model), the pose model whose legs we drive, and
    // the inner armor model passed to the boot render (FEET is inner armor).
    private record Holder(HumanoidArmorLayer<LivingEntity, HumanoidModel<LivingEntity>, HumanoidModel<LivingEntity>> layer,
                          HumanoidModel<LivingEntity> pose, HumanoidModel<LivingEntity> inner) {}

    private static final Map<String, Holder> HOLDERS = new HashMap<>();

    private SpiderBootsRenderer() {}

    /** True while we are drawing our own boots, so the host leg-armor suppressor lets the draw through. */
    public static boolean isRendering() {
        return RENDERING.get();
    }

    /**
     * Draw a boot on each leg the rig's {@code boots} block lists, fitted and tracking that leg. Call
     * inside the rig's scaled pose, after the rig model's {@code setupAnim} (so leg bones are current).
     * A no-op when the rig declares no boots or the entity wears none.
     */
    public static void render(LivingEntity entity, String rigBase, RigDefinition def, PoseStack pose,
                              MultiBufferSource buffers, int light) {
        List<RigDefinition.Boot> boots = def == null ? List.of() : def.boots();
        if (boots.isEmpty()) return;
        if (entity.getItemBySlot(EquipmentSlot.FEET).isEmpty()) return;
        java.lang.reflect.Method render = renderPiece();
        if (render == null) return;
        Holder holder = HOLDERS.computeIfAbsent(rigBase, b -> build());
        RENDERING.set(true);
        try {
            // One boot per entry, anchored to its leg bone. Pose the matching (left/right) leg slot and
            // zero the other so a single, correctly-mirrored boot draws.
            for (RigDefinition.Boot boot : boots) {
                ModelPart worn = boot.left() ? holder.pose.leftLeg : holder.pose.rightLeg;
                ModelPart other = boot.left() ? holder.pose.rightLeg : holder.pose.leftLeg;
                poseLeg(worn, RigModels.bakedBone(rigBase, boot.bone()), boot);
                hideLeg(other);
                render.invoke(holder.layer, pose, buffers, entity, EquipmentSlot.FEET, light, holder.inner);
            }
        } catch (ReflectiveOperationException ignored) {
            // A binding failure leaves the boots undrawn rather than crashing the render.
        } finally {
            RENDERING.set(false);
        }
    }

    /** Collapse a pose-model leg to nothing, so its boot does not draw alongside the per-bone one. */
    private static void hideLeg(ModelPart leg) {
        leg.resetPose();
        leg.xScale = 0f;
        leg.yScale = 0f;
        leg.zScale = 0f;
    }

    /** Resolve vanilla's per-slot armor render once, trying the official then the SRG method name. */
    private static java.lang.reflect.Method renderPiece() {
        if (!renderPieceResolved) {
            renderPieceResolved = true;
            for (String name : new String[]{"renderArmorPiece", "m_117118_"}) {
                try {
                    renderPiece = HumanoidArmorLayer.class.getDeclaredMethod(name, PoseStack.class,
                            MultiBufferSource.class, LivingEntity.class, EquipmentSlot.class, int.class,
                            HumanoidModel.class);
                    renderPiece.setAccessible(true);
                    break;
                } catch (NoSuchMethodException ignored) {
                    // try the next mapping
                }
            }
        }
        return renderPiece;
    }

    /**
     * Anchor a boot to its rig leg bone: copy the bone's live pivot and rotation (so the boot tracks
     * the walk), then apply the per-boot {@code scale} and {@code seat} — offset in the bone's local
     * frame (so it runs along the leg toward the foot) and rotation as a tilt on top. Each leg is tuned
     * individually because the legs sit at different angles and positions.
     */
    private static void poseLeg(ModelPart leg, ModelPart rigLeg, RigDefinition.Boot boot) {
        leg.resetPose();
        float s = boot.scale();
        leg.xScale = s;
        leg.yScale = s;
        leg.zScale = s;
        float bx = 0f, by = 0f, bz = 0f, bxr = 0f, byr = 0f, bzr = 0f;
        if (rigLeg != null) {
            bx = rigLeg.x; by = rigLeg.y; bz = rigLeg.z;
            bxr = rigLeg.xRot; byr = rigLeg.yRot; bzr = rigLeg.zRot;
        }
        RigDefinition.Adjust seat = boot.seat();
        float[] rot = seat.rotation();
        float xRot = bxr + (float) Math.toRadians(rot[0]);
        float yRot = byr + (float) Math.toRadians(rot[1]);
        float zRot = bzr + (float) Math.toRadians(rot[2]);
        float[] off = seat.offset();
        org.joml.Vector3f local = new org.joml.Vector3f(off[0], off[1], off[2]);
        new org.joml.Quaternionf().rotationZYX(zRot, yRot, xRot).transform(local);
        leg.x = bx + local.x();
        leg.y = by + local.y();
        leg.z = bz + local.z();
        leg.xRot = xRot;
        leg.yRot = yRot;
        leg.zRot = zRot;
    }

    private static Holder build() {
        HumanoidModel<LivingEntity> pose = genericHumanoid(0f);
        HumanoidModel<LivingEntity> inner = genericHumanoid(INNER_DEFORM);
        HumanoidModel<LivingEntity> outer = genericHumanoid(OUTER_DEFORM);
        RenderLayerParent<LivingEntity, HumanoidModel<LivingEntity>> parent = new BootParent(pose);
        HumanoidArmorLayer<LivingEntity, HumanoidModel<LivingEntity>, HumanoidModel<LivingEntity>> layer =
                new HumanoidArmorLayer<>(parent, inner, outer, Minecraft.getInstance().getModelManager());
        return new Holder(layer, pose, inner);
    }

    private static HumanoidModel<LivingEntity> genericHumanoid(float deform) {
        return new HumanoidModel<>(LayerDefinition.create(
                HumanoidModel.createMesh(new CubeDeformation(deform), 0.0f), 64, 32).bakeRoot());
    }

    /** Minimal parent so {@code renderArmorPiece} copies its pose (our positioned legs) onto the armor. */
    private record BootParent(HumanoidModel<LivingEntity> model)
            implements RenderLayerParent<LivingEntity, HumanoidModel<LivingEntity>> {
        @Override
        public HumanoidModel<LivingEntity> getModel() {
            return model;
        }

        @Override
        public ResourceLocation getTextureLocation(LivingEntity entity) {
            return net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation();
        }
    }
}
