package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.PartPreScale;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives {@link ModelPart} a scale applied between its pivot and its rotation; see {@link PartPreScale}.
 * A part without one transforms exactly as vanilla.
 * 1.20.1 Forge SRG: {@code m_104299_} translateAndRotate, {@code m_104315_} copyFrom.
 */
@Mixin(ModelPart.class)
public abstract class ModelPartPreScaleMixin implements PartPreScale {

    @Unique private float townstead$preX = 1f;
    @Unique private float townstead$preY = 1f;
    @Unique private float townstead$preZ = 1f;

    @Override
    public void townstead$setPreScale(float x, float y, float z) {
        townstead$preX = x;
        townstead$preY = y;
        townstead$preZ = z;
    }

    @Override
    public float townstead$preScaleX() {
        return townstead$preX;
    }

    @Override
    public float townstead$preScaleY() {
        return townstead$preY;
    }

    //? if neoforge {
    @Inject(method = "translateAndRotate", at = @At("HEAD"), cancellable = true, expect = 1)
    //?} else {
    /*@Inject(method = "m_104299_", remap = false, at = @At("HEAD"), cancellable = true, expect = 1)
    *///?}
    private void townstead$preScaleBeforeRotation(PoseStack pose, CallbackInfo ci) {
        if (townstead$preX == 1f && townstead$preY == 1f && townstead$preZ == 1f) return;
        ModelPart self = (ModelPart) (Object) this;
        pose.translate(self.x / 16.0F, self.y / 16.0F, self.z / 16.0F);
        pose.scale(townstead$preX, townstead$preY, townstead$preZ);
        if (self.xRot != 0f || self.yRot != 0f || self.zRot != 0f) {
            pose.mulPose(new Quaternionf().rotationZYX(self.zRot, self.yRot, self.xRot));
        }
        if (self.xScale != 1f || self.yScale != 1f || self.zScale != 1f) {
            pose.scale(self.xScale, self.yScale, self.zScale);
        }
        ci.cancel();
    }

    //? if neoforge {
    @Inject(method = "copyFrom", at = @At("TAIL"), expect = 1)
    //?} else {
    /*@Inject(method = "m_104315_", remap = false, at = @At("TAIL"), expect = 1)
    *///?}
    private void townstead$copyPreScale(ModelPart source, CallbackInfo ci) {
        ModelPartPreScaleMixin from = (ModelPartPreScaleMixin) (Object) source;
        townstead$setPreScale(from.townstead$preX, from.townstead$preY, from.townstead$preZ);
    }
}
