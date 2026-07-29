package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.ClimbState;
import com.aetherianartificer.townstead.client.species.RigModels;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses the vanilla swim pitch for the two cases where we own the body orientation instead.
 *
 * <p>{@code PlayerRenderer.setupRotations} lays the body flat along a -90 degree swim pitch whenever
 * {@code getSwimAmount > 0} (the {@code isVisuallySwimming} translate is nested inside that same branch),
 * which covers both halves of the SWIMMING pose: sprint-swimming, and crawling under a ceiling too low to
 * stand in. Forcing {@code getSwimAmount} to 0 skips the whole branch (pitch + offset). We do NOT touch
 * {@code isVisuallySwimming}: it is render-AND-physics state, and overriding it broke first-person climb
 * control. {@code getSwimAmount} is render-only (three call sites, all renderers), so suppressing it
 * changes nothing physical.</p>
 *
 * <p><b>Clung climbers.</b> A climber pressed against a ceiling has no room for the standing pose, so
 * vanilla forces it into the crawl pose and that pitch stacks on top of the climb tilt
 * ({@link com.aetherianartificer.townstead.client.species.ClimbRender}), leaving the body perpendicular to
 * the ceiling (a "T"). Vanilla swim movement never runs while clung anyway, since the climb controller
 * cancels {@code travel}.</p>
 *
 * <p><b>Non-humanoid rigs.</b> The pitch is a humanoid convention: it turns a standing biped into a
 * horizontal swimmer. A spider or other custom-geometry body has no "front" to pitch onto, so the same
 * rotation just lands it on its back with its face to the sky. These rigs stay upright and author their
 * own lean instead, via the rig's {@code poses.crawl} state (bones + body offset in
 * {@code SpeciesRigLayer}, body yaw/pitch/roll in {@code LivingEntityRendererCrawlMixin}) — the same split
 * {@code poses.sleep} already uses. Humanoid rigs are untouched and keep vanilla's pitch.</p>
 *
 * <p>1.20.1 Forge SRG: {@code m_20998_} getSwimAmount.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwimClimbMixin {

    //? if neoforge {
    @Inject(method = "getSwimAmount", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_20998_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$noSwimPitch(float partialTicks, CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide) return;
        if (townstead$clung(self) || townstead$ownsOrientation(self)) cir.setReturnValue(0f);
    }

    @Unique
    private static boolean townstead$clung(LivingEntity self) {
        return ClimbState.factor(self.getId()) > 0f;
    }

    @Unique
    private static boolean townstead$ownsOrientation(LivingEntity self) {
        return self.isVisuallySwimming() && RigModels.isGeneric(RigModels.rigBaseFor(self));
    }
}
