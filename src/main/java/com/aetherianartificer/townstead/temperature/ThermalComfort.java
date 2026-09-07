package com.aetherianartificer.townstead.temperature;

/** Villager behavior model, separate from the backend climate and the slow core simulation. */
public final class ThermalComfort {
    private ThermalComfort() {}

    public record State(float wetness, float load, float strainSeconds) {
        public static State read(net.minecraft.nbt.CompoundTag tag) {
            return new State(tag.contains("wetness") ? tag.getFloat("wetness") : tag.getBoolean("wet") ? 1 : 0,
                    tag.getFloat("comfortLoad"), tag.getFloat("thermalStrainSeconds"));
        }
        public void write(net.minecraft.nbt.CompoundTag tag) {
            tag.putFloat("wetness", wetness);
            tag.putFloat("comfortLoad", load);
            tag.putFloat("thermalStrainSeconds", strainSeconds);
        }
    }

    public static State update(State previous, float targetLoad, boolean immersed, boolean raining,
                               float ambient, float seconds, float dryingSeconds, float breakSeconds) {
        float wet = previous.wetness();
        if (immersed) wet = 1;
        else if (raining) wet = Math.min(1, wet + seconds / 30f);
        else wet = Math.max(0, wet - seconds / dryingSeconds * Math.max(0.25f, Math.min(2, ambient / 20f)));
        float load = previous.load() + (targetLoad - previous.load()) * (1 - (float) Math.exp(-seconds / 5f));
        if (immersed) load = targetLoad;
        boolean changedSide = Math.signum(load) != Math.signum(previous.load());
        float strain = changedSide ? 0 : previous.strainSeconds();
        strain = Math.abs(load) >= 6 ? Math.min(breakSeconds, strain + seconds)
                : Math.max(0, strain - seconds * 2);
        return new State(wet, load, strain);
    }

    /** Temperature-equivalent behavioral load. It is not a measured skin/body temperature. */
    public static float load(float ambient, float wetness, boolean immersed, float activityWarmth,
                             ThermalProtection clothing, ThermalProfile profile) {
        float retained = 1 - 0.75f * wetness;
        ThermalProtection protection = new ThermalProtection(0, clothing.coldResistance() * retained,
                clothing.heatResistance(), clothing.thermalResistance() * retained);
        float air = protection.protectAmbient(ambient, TemperatureData.AMBIENT_REFERENCE);
        float gap = air - TemperatureData.AMBIENT_REFERENCE;
        float insulation = (profile.insulation() + clothing.offset() * retained)
                / TemperatureData.AMBIENT_PULL_PER_DEGREE;
        if (insulation > 0) {
            if (gap < 0) gap = Math.min(0, gap + insulation);
            else if (!profile.sheds()) gap += insulation * 0.35f;
        } else gap += insulation;
        float water = immersed ? Math.max(0, 20 - ambient) * 0.75f : 0;
        gap += activityWarmth - wetness * 8 - water;
        float sensitivity = gap < 0 ? profile.cold() : profile.heat();
        return sensitivity == 0 ? 0 : gap * sensitivity;
    }

    public static TemperatureData.Tier tier(float load) {
        if (Math.abs(load) <= 2) return TemperatureData.Tier.COMFORTABLE;
        if (load < 0) return load > -6 ? TemperatureData.Tier.CHILLY
                : load > -12 ? TemperatureData.Tier.COLD : TemperatureData.Tier.FREEZING;
        return load < 6 ? TemperatureData.Tier.WARM
                : load < 12 ? TemperatureData.Tier.HOT : TemperatureData.Tier.SWELTERING;
    }

    public static boolean needsBreak(float load, float strain, float breakSeconds) {
        return Math.abs(load) >= 12 || (Math.abs(load) >= 6 && strain >= breakSeconds);
    }
}
