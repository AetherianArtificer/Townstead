package com.aetherianartificer.townstead.mixin.compat.temperature;
import com.aetherianartificer.townstead.compat.temperature.RoomHeatBackend;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "sfiomn.legendarysurvivaloverhaul.common.temperature.BlockModifier")
public class LsoOutdoorBlocksMixin {
    @Inject(method = "getWorldInfluence", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void townstead$outside(Player player, Level level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (RoomHeatBackend.outdoorSampling()) cir.setReturnValue(0f);
    }
}
