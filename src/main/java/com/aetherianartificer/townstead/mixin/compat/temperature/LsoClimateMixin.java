package com.aetherianartificer.townstead.mixin.compat.temperature;

import com.aetherianartificer.townstead.compat.temperature.LsoClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "sfiomn.legendarysurvivaloverhaul.common.temperature.WeatherModifier")
public class LsoClimateMixin {
    // This common modifier feeds world queries, outdoor room reservoirs and player targets.
    @Inject(method = "getWorldInfluence", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void townstead$climate(Player player, Level level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(LsoClimate.weather(player, level, pos, cir.getReturnValue()));
    }
}
