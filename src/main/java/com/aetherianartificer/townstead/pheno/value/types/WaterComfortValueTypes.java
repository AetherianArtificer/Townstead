package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.aetherianartificer.townstead.temperature.ComfortRange;
import com.aetherianartificer.townstead.temperature.ThermalAir;
import com.aetherianartificer.townstead.temperature.ThermalComfort;
import com.aetherianartificer.townstead.temperature.ThermalExposure;
import com.aetherianartificer.townstead.temperature.ThermalProfile;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * How far the water at the focus block is outside what the entity is comfortable in, in degrees, by Townstead's
 * temperature and the active temperature mod's comfort range. 0 when it is comfortable, and always 0 with no temperature
 * mod. For bathing: {@code { "type":"pheno:value", "value":{ "type":"pheno:water_chill" }, "comparison":"<=", "compare_to":6 }}.
 *
 * <ul>
 *   <li>{@code pheno:water_chill}: degrees under the cool end of comfort.</li>
 *   <li>{@code pheno:water_heat}: degrees past the warm end, as water feels: like the middle of the range, moved for each
 *       degree it is off neutral water.</li>
 * </ul>
 */
public final class WaterComfortValueTypes {

    private WaterComfortValueTypes() {}

    public static final class Chill implements ValueType {
        public static final String KEY = "pheno:water_chill";

        @Override public String key() { return KEY; }

        @Override public Value parse(JsonObject json) {
            return ctx -> {
                ComfortRange range = range(ctx);
                if (range == null) return 0;
                return Math.max(0f, range.low() - ThermalAir.water((ServerLevel) ctx.level(), ctx.focusBlock()));
            };
        }
    }

    public static final class Heat implements ValueType {
        public static final String KEY = "pheno:water_heat";

        @Override public String key() { return KEY; }

        @Override public Value parse(JsonObject json) {
            return ctx -> {
                ComfortRange range = range(ctx);
                if (range == null) return 0;
                BlockPos at = ctx.focusBlock();
                float feels = range.mid() + (ThermalAir.water((ServerLevel) ctx.level(), at) - ThermalComfort.NEUTRAL_WATER)
                        * ThermalComfort.WATER_FACTOR;
                return Math.max(0f, feels - range.high());
            };
        }
    }

    /** The entity's comfort range, or null with no temperature mod or nothing to ask about. */
    private static ComfortRange range(SelectorContext ctx) {
        if (!TownsteadConfig.temperatureActive() || !(ctx.level() instanceof ServerLevel)) return null;
        LivingEntity self = ctx.self();
        if (self == null) return null;
        float acclimation = self instanceof VillagerEntityMCA villager ? ThermalExposure.acclimation(villager) : 0f;
        return ComfortRange.of(ThermalProfile.of(self), acclimation);
    }
}
