package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.compat.temperature.ColdSweatEntityCompat;
import com.aetherianartificer.townstead.compat.temperature.LsoEntityCompat;
import com.aetherianartificer.townstead.compat.temperature.ToughAsNailsEntityCompat;
import com.aetherianartificer.townstead.root.needs.NeedSuppression;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** The same personal inputs and prediction used by the body, wardrobe and relief planner. */
public record ThermalExposure(float ambient, float wetness, boolean immersed, boolean raining,
                              float activity, ThermalProtection protection, ThermalProfile profile,
                              boolean sleeping, float humidity, float water, ComfortRange range) {
    /** Neutral humidity, water at the air's temperature, and Townstead's range moved by the Root's offset. */
    public ThermalExposure(float ambient, float wetness, boolean immersed, boolean raining,
                           float activity, ThermalProtection protection, ThermalProfile profile, boolean sleeping) {
        this(ambient, wetness, immersed, raining, activity, protection, profile, sleeping, ThermalComfort.neutralHumidity(ambient), ambient,
                TemperatureSettings.get().comfortRange().offset(profile.comfortOffset()));
    }

    public static boolean enabled(VillagerEntityMCA villager) {
        return com.aetherianartificer.townstead.TownsteadConfig.isVillagerTemperatureEnabled()
                && !NeedSuppression.suppressesTemperature(villager)
                && !ThermalProfile.of(villager).suppressed()
                && !ToughAsNailsEntityCompat.climateClemency(villager)
                && !ColdSweatEntityCompat.grace(villager);
    }

    public static ThermalProtection protection(VillagerEntityMCA villager) {
        return Insulation.clothingProtection(villager).plus(ToughAsNailsEntityCompat.protection(villager))
                .plus(LsoEntityCompat.effects(villager).protection())
                .plus(ColdSweatEntityCompat.protection(villager));
    }

    public static ThermalExposure at(ServerLevel level, VillagerEntityMCA villager, BlockPos pos, float activity) {
        float air = TemperatureData.ambientCelsius(level, pos);
        boolean immersed = !level.getFluidState(pos).isEmpty();
        ThermalProfile profile = ThermalProfile.of(villager);
        return new ThermalExposure(ThermalConsumables.internalAmbient(villager, air),
                TownsteadVillagers.get(villager).needs().thermalWetness(), immersed,
                level.isRainingAt(pos), activity, protection(villager), profile, villager.isSleeping(),
                ThermalAir.humidity(level, pos, air), immersed ? ThermalAir.water(level, pos) : air,
                ComfortRange.of(profile, acclimation(villager)));
    }

    /** The villager's acclimation, from -1 cold-adapted to 1 hot-adapted. */
    public static float acclimation(VillagerEntityMCA villager) {
        return TownsteadVillagers.get(villager).needs().thermalAcclimation();
    }

    /** Sustained exposure after meals/potions expire; shelter must also work without a temporary buff. */
    public static ThermalExposure sustainedAt(ServerLevel level, VillagerEntityMCA villager, BlockPos pos, float activity) {
        float air = TemperatureData.ambientCelsius(level, pos);
        boolean immersed = !level.getFluidState(pos).isEmpty();
        ThermalProfile profile = ThermalProfile.of(villager);
        return new ThermalExposure(air,
                TownsteadVillagers.get(villager).needs().thermalWetness(), immersed,
                level.isRainingAt(pos), activity, Insulation.clothingProtection(villager).plus(ToughAsNailsEntityCompat.equipmentProtection(villager)),
                profile, villager.isSleeping(), ThermalAir.humidity(level, pos, air), immersed ? ThermalAir.water(level, pos) : air,
                ComfortRange.of(profile, acclimation(villager)));
    }

    public ThermalExposure withProtection(ThermalProtection next) {
        return new ThermalExposure(ambient, wetness, immersed, raining, activity, next, profile, sleeping, humidity, water, range);
    }

    public float feelsLike() {
        return ThermalComfort.feelsLike(ambient, humidity, immersed ? 1 : wetness, immersed, water, ThermalComfort.metabolism(activity), protection, profile, range);
    }
    public float load() { return ThermalComfort.load(feelsLike(), range, profile); }
    public float target() { return BodyHeat.target(load(), profile, sleeping); }

    public record Forecast(float body, float target, int recoverySeconds) {
        public boolean recovers() { return recoverySeconds >= 0; }
    }

    /** Forecast gradual drying; never assume a wet villager becomes dry by entering cover. */
    public Forecast forecast(float body, int seconds) {
        var settings = TemperatureSettings.get();
        double next = body;
        float wet = wetness, target = target();
        int recovery = BodyHeat.reliefComplete(body, target, profile) ? 0 : -1;
        for (int elapsed = 0; elapsed < seconds;) {
            int step = Math.min(5, seconds - elapsed);
            float load = ThermalComfort.load(ambient, humidity, immersed ? 1 : wet, immersed, water, ThermalComfort.metabolism(activity), protection, profile, range);
            target = BodyHeat.target(load, profile, sleeping);
            double before = next;
            next = BodyHeat.predict((float) next, target, profile, step);
            wet = ThermalComfort.update(new ThermalComfort.State(wet, load, 0), load, immersed, raining,
                    ambient, humidity, step, settings.dryingSeconds()).wetness();
            elapsed += step;
            if (recovery < 0 && (BodyHeat.reliefComplete((float) next, target, profile)
                    || (before - profile.neutral()) * (next - profile.neutral()) < 0)) recovery = elapsed;
        }
        return new Forecast((float) next, target, recovery);
    }

    public float outfitCost() {
        // A whole working interval, not a momentary body nudge; zero inside a safe target band.
        return Math.max(0, Math.abs(target() - profile.neutral()) - profile.band() * .45f);
    }

    public static float activity(VillagerEntityMCA villager) {
        if (villager.getVillagerBrain().isPanicking() || villager.getLastHurtByMob() != null) return 6;
        if (villager.swinging) return 4;
        return villager.getDeltaMovement().horizontalDistanceSqr() > .0004 ? 2 : 0;
    }
}
