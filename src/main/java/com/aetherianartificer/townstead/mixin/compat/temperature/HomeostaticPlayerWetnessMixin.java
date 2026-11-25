package com.aetherianartificer.townstead.mixin.compat.temperature;

import com.aetherianartificer.townstead.temperature.PlayerFeelsLike;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** See {@link HomeostaticPlayerClothingMixin}: wetness's effect on skin temperature is Townstead's. */
@Pseudo
@Mixin(targets = "homeostatic.common.temperature.BodyTemperature", remap = false)
public class HomeostaticPlayerWetnessMixin {
    @WrapOperation(method = "<init>(Lnet/minecraft/server/level/ServerPlayer;Lhomeostatic/common/temperature/EnvironmentData;Lhomeostatic/network/ITemperature;ZZ)V",
            at = @At(value = "INVOKE", target = "Lhomeostatic/util/WetnessHelper;getWetness(Lnet/minecraft/server/level/ServerPlayer;)I"),
            require = 0)
    private int townstead$wetness121(ServerPlayer player, Operation<Integer> original) {
        return PlayerFeelsLike.active() ? 0 : original.call(player);
    }

    @WrapOperation(method = "<init>(Lnet/minecraft/server/level/ServerPlayer;Lhomeostatic/common/temperature/EnvironmentData;Lhomeostatic/common/capabilities/ITemperature;ZZ)V",
            at = @At(value = "INVOKE", target = "Lhomeostatic/util/WetnessHelper;getWetness(Lnet/minecraft/server/level/ServerPlayer;)I"),
            require = 0)
    private int townstead$wetness120(ServerPlayer player, Operation<Integer> original) {
        return PlayerFeelsLike.active() ? 0 : original.call(player);
    }
}
