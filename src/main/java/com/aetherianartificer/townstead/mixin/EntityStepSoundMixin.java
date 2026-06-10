package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.habitus.sound.SoundSpec;
import com.aetherianartificer.townstead.origin.gene.types.CustomSoundGeneType.Slot;
import com.aetherianartificer.townstead.origin.sound.CustomSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a {@code custom_sound} gene replace a living entity's footstep sound (Apugli's
 * {@code custom_footstep}): plays the gene's sound and cancels vanilla's block-step
 * sound. Targets {@code Entity.playStepSound} ({@code m_7355_} on the 1.20.1 Forge build)
 * and guards to {@link LivingEntity}, since the gene only applies to creatures.
 */
@Mixin(Entity.class)
public abstract class EntityStepSoundMixin {

    //? if neoforge {
    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_7355_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$stepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!((Object) this instanceof LivingEntity living)) return;
        SoundSpec.Entry entry = CustomSounds.pick(living, Slot.STEP);
        if (entry != null) {
            living.playSound(entry.sound(), entry.volume(), entry.pitch());
            ci.cancel();
        }
    }
}
