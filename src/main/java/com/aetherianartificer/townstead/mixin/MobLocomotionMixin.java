package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.locomotion.Locomotion;
import com.aetherianartificer.townstead.root.locomotion.LocomotionCarrier;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Carries the {@link Locomotion} state and, while a pilot has the body, skips the
 * ground controls in {@code serverAiStep} (navigation only when the pilot owns it).
 * Wrapping the call sites skips subclass overrides too (MCA's navigation and move
 * control), with no change to MCA.
 * Parity: SRG names on Forge.
 */
@Mixin(Mob.class)
public abstract class MobLocomotionMixin implements LocomotionCarrier {

    @Unique
    @Nullable
    private Locomotion.State townstead$locomotion;

    @Unique
    @Override
    public Locomotion.State townstead$locomotion() {
        return townstead$locomotion;
    }

    @Unique
    @Override
    public void townstead$setLocomotion(Locomotion.State state) {
        townstead$locomotion = state;
    }

    //? if neoforge {
    @WrapOperation(method = "serverAiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;tick()V"))
    //?} else {
    /*@WrapOperation(method = "m_6140_", remap = false, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;m_7638_()V"))
    *///?}
    private void townstead$navigationTick(PathNavigation navigation, Operation<Void> original) {
        if (!Locomotion.suspendsNavigation(this)) original.call(navigation);
    }

    //? if neoforge {
    @WrapOperation(method = "serverAiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/control/MoveControl;tick()V"))
    //?} else {
    /*@WrapOperation(method = "m_6140_", remap = false, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/control/MoveControl;m_8126_()V"))
    *///?}
    private void townstead$moveControlTick(MoveControl control, Operation<Void> original) {
        if (!Locomotion.suspendsBodyControl(this)) original.call(control);
    }

    //? if neoforge {
    @WrapOperation(method = "serverAiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/control/JumpControl;tick()V"))
    //?} else {
    /*@WrapOperation(method = "m_6140_", remap = false, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/control/JumpControl;m_8124_()V"))
    *///?}
    private void townstead$jumpControlTick(JumpControl control, Operation<Void> original) {
        if (!Locomotion.suspendsBodyControl(this)) original.call(control);
    }
}
