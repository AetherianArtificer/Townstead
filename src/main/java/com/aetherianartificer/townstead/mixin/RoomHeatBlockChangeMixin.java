package com.aetherianartificer.townstead.mixin;
import com.aetherianartificer.townstead.temperature.RoomHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class RoomHeatBlockChangeMixin {
    //? if neoforge {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "m_6933_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", remap = false, at = @At("RETURN"))
    *///?}
    private void townstead$changed(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && (Object)this instanceof ServerLevel server) {
            com.aetherianartificer.townstead.temperature.ThermalSourceIndex.changed(server, pos, state);
            RoomHeat.changed(server, pos, state);
        }
    }
}
