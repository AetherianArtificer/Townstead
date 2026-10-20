package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.aetherianartificer.townstead.temperature.ThermalExposure;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;

/**
 * The air temperature in Celsius at the focus: rooms, ground and nearby heat included.
 * {@code "felt": true} reads what a villager feels instead, after clothing, wetness and work.
 *
 * <p>JSON: {@code { "type":"pheno:air_temperature", "felt": false }}</p>
 */
public final class AirTemperatureValueType implements ValueType {
    public static final String KEY = "pheno:air_temperature";

    @Override public String key() { return KEY; }

    @Override public Value parse(JsonObject json) {
        boolean felt = GsonHelper.getAsBoolean(json, "felt", false);
        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel level)) return 0;
            BlockPos pos = ctx.focusBlock();
            if (felt && ctx.self() instanceof net.conczin.mca.entity.VillagerEntityMCA villager) {
                return ThermalExposure.at(level, villager, villager.blockPosition(), ThermalExposure.activity(villager)).ambient();
            }
            return TemperatureData.ambientCelsius(level, pos);
        };
    }
}
