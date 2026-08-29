package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.mca.McaBuildingDiscovery;
import com.aetherianartificer.townstead.compat.mca.BuildingBlockQuantity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures player, piston, explosion, and mod-driven block replacements through one shared path. */
@Mixin(Level.class)
public abstract class ServerLevelBuildingDiscoveryMixin {
    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"),
            require = 0)
    private void townstead$scheduleMcaBuildingDiscovery(
            BlockPos pos, BlockState newState, int flags, int recursionLeft,
            CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerLevel level) || pos == null || newState == null) return;
        BlockState oldState = level.getBlockState(pos);
        // Most same-block state changes cannot alter classification. Stateful stacks are the
        // exception: Bakery jars keep several physical bottles in one tile via a `stack` property.
        if (oldState.getBlock() == newState.getBlock()
                && BuildingBlockQuantity.units(oldState) == BuildingBlockQuantity.units(newState)) return;
        McaBuildingDiscovery.onBlockChanged(level, pos, oldState, newState);
    }
}
