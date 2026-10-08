package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;

/** Server-side Pheno values; normalized stress avoids assuming a human neutral for every root. */
public final class ThermalQueries {
    private ThermalQueries() {}
    public static double value(ConditionContext context, String name) {
        if (!(context.entity() instanceof VillagerEntityMCA villager) || context.level().isClientSide) return Double.NaN;
        var needs = TownsteadVillagers.get(villager).needs();
        if (!needs.hasBodyTemp() || !ThermalExposure.enabled(villager)) return Double.NaN;
        var profile = ThermalProfile.of(villager);
        float body = TemperatureData.celsius(needs.bodyTempTenths());
        return switch (name) {
            case "thermal_stress" -> (body - profile.neutral()) / Math.max(.05f, profile.band());
            case "thermal_wetness" -> needs.thermalWetness();
            case "thermal_load" -> needs.comfortLoad();
            case "thermal_strain" -> needs.thermalStrainSeconds();
            case "thermal_trend" -> Math.signum(BodyHeat.target(needs.comfortLoad(), TemperatureSettings.get().comfortZone(), profile, villager.isSleeping()) - body);
            default -> Double.NaN;
        };
    }
}
