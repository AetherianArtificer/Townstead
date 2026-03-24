package com.aetherianartificer.townstead.mixin.compat.farmersdelight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.StoveBlock")
public abstract class StoveVillagerDamageMixin {
    //? if >=1.21 {
    @Inject(method = "stepOn", at = @At("HEAD"), cancellable = true, require = 0)
    //?} else {
    /*@Inject(method = "m_141947_", remap = false, at = @At("HEAD"), cancellable = true, require = 0)
    *///?}
    private void townstead$skipStoveDamageForVillagers(
            Level level, BlockPos pos, BlockState state, Entity entity, CallbackInfo ci
    ) {
        if (entity instanceof Villager) {
            ci.cancel();
        }
    }
}
