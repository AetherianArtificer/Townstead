package com.aetherianartificer.townstead.mixin.compat.temperature;

import com.aetherianartificer.townstead.compat.temperature.HomeostaticTemperatureBridge;
import com.aetherianartificer.townstead.compat.temperature.RoomHeatBackend;
import com.aetherianartificer.townstead.temperature.PlayerFeelsLike;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Homeostatic measures a player's surroundings once per update, here. Under Townstead's climate
 * authority the result becomes Townstead's environment (rooms, climate, and heat sources when
 * Townstead owns block heat), moved to what the player feels while Townstead owns feels-like.
 * Immersion is then Townstead's too, as for Cold Sweat and Tough As Nails.
 */
@Pseudo
@Mixin(targets = "homeostatic.common.temperature.EnvironmentData", remap = false)
public abstract class HomeostaticRoomQueryMixin {
    @Shadow private float localTemperature;
    @Shadow private float airTemperature;
    @Shadow private boolean isSubmerged;
    @Shadow private boolean isPartialSubmersion;
    @Shadow private double envRadiation;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void townstead$environment(ServerPlayer player, BlockPos pos, Holder<?> biome, ServerLevel level,
                                       CallbackInfo ci) {
        var environment = RoomHeatBackend.environment(level, pos);
        if (environment.isEmpty()) return;
        float air = (float) environment.getAsDouble();
        // Without Townstead block heat the mod keeps its own heat sources on top of the climate.
        if (!RoomHeatBackend.ownsBlockHeat()) air += (float) envRadiation * HomeostaticTemperatureBridge.RADIATION_CELSIUS;
        if (PlayerFeelsLike.active()) {
            float wet = HomeostaticTemperatureBridge.INSTANCE.playerWetness(player);
            air = PlayerFeelsLike.sent(player, air, Float.isFinite(wet) ? wet : 0f);
            isSubmerged = false;
            isPartialSubmersion = false;
        }
        float units = HomeostaticTemperatureBridge.units(air);
        localTemperature = units;
        airTemperature = units;
    }
}
