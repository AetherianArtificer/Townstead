package com.aetherianartificer.townstead.mixin.compat.temperature;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "sfiomn.legendarysurvivaloverhaul.util.WorldUtil")
public class LsoThermometerMixin {
    @Inject(method = "calculateClientWorldEntityTemperature", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void townstead$serverReading(Level level, Entity entity, CallbackInfoReturnable<Float> cir) {
        if (!level.isClientSide) return;
        var reading = com.aetherianartificer.townstead.temperature.PlayerEnvironmentClient.at(level, entity);
        if (reading.isPresent()) cir.setReturnValue((float) reading.getAsDouble());
    }
}
