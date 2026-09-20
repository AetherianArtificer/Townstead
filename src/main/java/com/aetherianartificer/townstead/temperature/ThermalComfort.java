package com.aetherianartificer.townstead.temperature;

/** How the surroundings feel, and how wet the villager is. The body itself is {@link BodyHeat}. */
public final class ThermalComfort {
    private ThermalComfort() {}

    /** Persisted feel: wetness, and the latest environment load for display and hangout choice. */
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

    /** Wetness soaks in rain or water and dries faster in warm air; the load is taken as felt now. */
    public static State update(State previous, float load, boolean immersed, boolean raining,
                               float ambient, float seconds, float dryingSeconds) {
        float wet = previous.wetness();
        if (immersed) wet = 1;
        else if (raining) wet = Math.min(1, wet + seconds / 30f);
        else wet = Math.max(0, wet - seconds / dryingSeconds * Math.max(0.25f, Math.min(2, ambient / 20f)));
        return new State(wet, load, 0);
    }

    /** Temperature-equivalent environment load: ambient after clothing, genes, wetness, water and activity, less 20. */
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
            else if (!profile.sheds()) gap += insulation * 0.35f * Math.min(1f, gap / 6f);
        } else gap += insulation;
        float water = immersed ? Math.max(0, 20 - ambient) * 0.75f : 0;
        gap += activityWarmth - wetness * 8 - water;
        float sensitivity = gap < 0 ? profile.cold() : profile.heat();
        return sensitivity == 0 ? 0 : gap * sensitivity;
    }
}
