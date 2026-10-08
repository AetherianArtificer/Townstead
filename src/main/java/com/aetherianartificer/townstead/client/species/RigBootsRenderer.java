package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.root.rig.RigDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
 * Draws a worn boot on each bone a rig's {@code boots} block names (e.g. one per leg of a multi-legged
 * rig). Vanilla only draws two boots; here we drive vanilla's own per-slot armor render once per bone so
 * every material's texture, dye, trim and glint comes for free, and we only supply where each boot sits.
 *
 * <p>Model-agnostic: nothing here knows what the rig is. Placement is fully dynamic: for each named bone we
 * push that bone's live transform onto the pose stack with {@link ModelPart#translateAndRotate} (the same
 * primitive held items use), walk out to the bone's TIP, and draw the boot there. Because we move through
 * the bone's real frame instead of reconstructing it from euler angles, the boot lands at the foot of the
 * bone whatever its pitch/yaw, and tracks the walk. The bone is read post-{@code setupAnim}. Per boot, the
 * rig JSON's {@code rotation} orients the boot onto its bone (each may point a different way) and
 * {@code offset} nudges it; both are applied in the bone's tip frame.</p>
 */
public final class RigBootsRenderer {

    private static final ThreadLocal<Boolean> RENDERING = ThreadLocal.withInitial(() -> false);
    private static final float INNER_DEFORM = 0.5f;
    private static final float OUTER_DEFORM = 1.0f;
    // The boot model is a humanoid leg whose foot sits this many (unscaled) pixels below the part pivot;
    // lifting the pose by it lands the boot's sole at the bone tip when offset/rotation are zero.
    private static final float FOOT_DROP = 12f;
    // A fixed source for getRandomCube; a one-cube bone returns its only cube regardless of the value.
    private static final net.minecraft.util.RandomSource CUBE_PICK = net.minecraft.util.RandomSource.create(0L);

    // Vanilla's private per-slot armor render, resolved by reflection (official name on NeoForge, SRG on
    // Forge). Reused verbatim so dye/trim/glint/material handling all come for free.
    private static java.lang.reflect.Method renderPiece;
    private static boolean renderPieceResolved;

    // Per rig: the armor layer (its parent IS the posed armor model) and that armor model whose legs we
    // draw. With the parent and the rendered model the same, renderArmorPiece's internal copy is a
    // self-copy, so the flat leg we set survives to the draw and the boot sits exactly at the pose frame.
    private record Holder(HumanoidArmorLayer<LivingEntity, HumanoidModel<LivingEntity>, HumanoidModel<LivingEntity>> layer,
                          HumanoidModel<LivingEntity> inner) {}

    private static final Map<String, Holder> HOLDERS = new HashMap<>();

    private RigBootsRenderer() {}

    /** True while we are drawing our own boots, so the host leg-armor suppressor lets the draw through. */
    public static boolean isRendering() {
        return RENDERING.get();
    }

    /**
     * Draw a boot on each bone the rig's {@code boots} block lists, fitted to that bone's tip and tracking
     * the walk. Call inside the rig's scaled pose, after the rig model's {@code setupAnim}. A no-op when
     * the rig declares no boots or the entity wears none.
     */
    public static void render(LivingEntity entity, String rigBase, RigDefinition def, PoseStack pose,
                              MultiBufferSource buffers, int light) {
        List<RigDefinition.Boot> boots = def == null ? List.of() : def.boots();
        if (boots.isEmpty()) return;
        if (entity.getItemBySlot(EquipmentSlot.FEET).isEmpty()) return;
        java.lang.reflect.Method render = renderPiece();
        if (render == null) return;
        ModelPart root = RigModels.bakedRoot(rigBase);
        Holder holder = HOLDERS.computeIfAbsent(rigBase, b -> build());
        RENDERING.set(true);
        try {
            for (RigDefinition.Boot boot : boots) {
                ModelPart bone = RigModels.bakedBone(rigBase, boot.bone());
                if (bone == null) continue;
                // The worn leg draws flat (at the pose frame); the other is collapsed so only one boot draws.
                ModelPart worn = boot.left() ? holder.inner.leftLeg : holder.inner.rightLeg;
                ModelPart other = boot.left() ? holder.inner.rightLeg : holder.inner.leftLeg;
                float s = boot.scale() * boot.seat().scale();
                flatLeg(worn, s);
                worn.xScale *= boot.seat().scaleAxes()[0];
                worn.yScale *= boot.seat().scaleAxes()[1];
                worn.zScale *= boot.seat().scaleAxes()[2];
                hideLeg(other);

                pose.pushPose();
                // Walk into the bone's live frame: root then the bone (vanilla leg bones are direct
                // children of the root, whose own transform is identity). This is the dynamic tip finder.
                if (root != null) root.translateAndRotate(pose);
                RigModels.translateToParent(rigBase, boot.bone(), pose);
                bone.translateAndRotate(pose);
                // Out to the bone's tip (its cube's far corner), in the bone's own frame.
                org.joml.Vector3f tip = boneTip(bone);
                pose.translate(tip.x() / 16f, tip.y() / 16f, tip.z() / 16f);
                // Authored orientation, then offset, in the tip frame: dial each boot onto its bone.
                RigDefinition.Adjust seat = boot.seat();
                float[] rot = seat.rotation();
                if (rot[0] != 0f) pose.mulPose(Axis.XP.rotationDegrees(rot[0]));
                if (rot[1] != 0f) pose.mulPose(Axis.YP.rotationDegrees(rot[1]));
                if (rot[2] != 0f) pose.mulPose(Axis.ZP.rotationDegrees(rot[2]));
                float[] off = seat.offset();
                pose.translate(off[0] / 16f, off[1] / 16f, off[2] / 16f);
                // Lift so the boot's sole lands on the tip instead of hanging a leg-length below it.
                pose.translate(0f, -FOOT_DROP / 16f * s, 0f);
                render.invoke(holder.layer, pose, buffers, entity, EquipmentSlot.FEET, light, holder.inner);
                pose.popPose();
            }
        } catch (ReflectiveOperationException ignored) {
            // A binding failure leaves the boots undrawn rather than crashing the render.
        } finally {
            RENDERING.set(false);
        }
    }

    /** Draw the worn leg flat at the pose frame: pivot at the origin, no rotation, uniform scale. */
    private static void flatLeg(ModelPart leg, float s) {
        leg.x = 0f;
        leg.y = 0f;
        leg.z = 0f;
        leg.xRot = 0f;
        leg.yRot = 0f;
        leg.zRot = 0f;
        leg.xScale = s;
        leg.yScale = s;
        leg.zScale = s;
    }

    /** Collapse a leg to nothing so its boot does not draw alongside the per-bone one. */
    private static void hideLeg(ModelPart leg) {
        leg.xScale = 0f;
        leg.yScale = 0f;
        leg.zScale = 0f;
    }

    /** The bone's tip in its local frame: the corner of its (representative) cube farthest from the pivot. */
    public static org.joml.Vector3f boneTip(ModelPart leg) {
        if (leg == null) return new org.joml.Vector3f();
        try {
            CUBE_PICK.setSeed(0L);
            ModelPart.Cube cube = leg.getRandomCube(CUBE_PICK);
            float x = Math.abs(cube.minX) > Math.abs(cube.maxX) ? cube.minX : cube.maxX;
            float y = Math.abs(cube.minY) > Math.abs(cube.maxY) ? cube.minY : cube.maxY;
            float z = Math.abs(cube.minZ) > Math.abs(cube.maxZ) ? cube.minZ : cube.maxZ;
            return new org.joml.Vector3f(x, y, z);
        } catch (RuntimeException e) {
            return new org.joml.Vector3f();
        }
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

    private static Holder build() {
        HumanoidModel<LivingEntity> inner = genericHumanoid(INNER_DEFORM);
        HumanoidModel<LivingEntity> outer = genericHumanoid(OUTER_DEFORM);
        RenderLayerParent<LivingEntity, HumanoidModel<LivingEntity>> parent = new BootParent(inner);
        HumanoidArmorLayer<LivingEntity, HumanoidModel<LivingEntity>, HumanoidModel<LivingEntity>> layer =
                new HumanoidArmorLayer<>(parent, inner, outer, Minecraft.getInstance().getModelManager());
        return new Holder(layer, inner);
    }

    private static HumanoidModel<LivingEntity> genericHumanoid(float deform) {
        return new HumanoidModel<>(LayerDefinition.create(
                HumanoidModel.createMesh(new CubeDeformation(deform), 0.0f), 64, 32).bakeRoot());
    }

    /** Minimal parent so the armor layer copies its pose (our flat legs) onto the armor model. */
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
