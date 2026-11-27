package com.aetherianartificer.townstead.mixin.compat.temperature;

import com.aetherianartificer.townstead.temperature.PlayerFeelsLike;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While Townstead owns what a player feels, clothing and wetness are already in the environment
 * Homeostatic receives, so its own armor insulation and wetness terms step aside, the way LSO's
 * wet and resistance modifiers and TAN's armor and immersion modifiers do. Its wetness tracking
 * itself is untouched; Townstead reads it.
 */
@Pseudo
@Mixin(targets = "homeostatic.util.InsulationHelper", remap = false)
public class HomeostaticPlayerClothingMixin {
    @Inject(method = "getInsulationModifier(Lnet/minecraft/server/level/ServerPlayer;ILhomeostatic/common/temperature/TemperatureDirection;F)D",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void townstead$clothing(ServerPlayer player, int wetness, @Coerce Object direction, float local,
                                           CallbackInfoReturnable<Double> cir) {
        if (PlayerFeelsLike.active()) cir.setReturnValue(0.0);
    }
}
