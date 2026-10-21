package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.climate.Evaporation;

/**
 * How the surroundings feel, as one number in Celsius ("feels like"), and how wet the body is. The
 * body itself is {@link BodyHeat}; it judges feels like against a {@link ComfortRange}.
 */
public final class ThermalComfort {
    private ThermalComfort() {}

    /** Water a body neither gains nor loses heat in, and how much harder each degree away from it counts. */
    static final float NEUTRAL_WATER = 33f, WATER_FACTOR = 1.5f;
    /** Wet-bulb temperature where sweat stops cooling a body. */
    static final float WET_BULB_LIMIT = 31f;
    /** Most a wet body chills by evaporation, as in hot desert air. */
    static final double MAX_EVAPORATIVE_CHILL = 10;

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

    public static State update(State previous, float load, boolean immersed, boolean raining,
                               float ambient, float seconds, float dryingSeconds) {
        return update(previous, load, immersed, raining, ambient, 0.5f, seconds, dryingSeconds);
    }

    /** Wetness soaks in rain or water, and dries as fast as the air is thirsty. */
    public static State update(State previous, float load, boolean immersed, boolean raining,
                               float ambient, float humidity, float seconds, float dryingSeconds) {
        float wet = previous.wetness();
        if (immersed) wet = 1;
        else if (raining) wet = Math.min(1, wet + seconds / 30f);
        else wet = Math.max(0, wet - seconds / dryingSeconds * drying(ambient, humidity));
        return new State(wet, load, 0);
    }

    /** Drying speed against mild air (20 C, half humid): fast in a desert, slow in a jungle. */
    static float drying(float air, float humidity) {
        double factor = Evaporation.deficit(air, humidity) / Evaporation.deficit(20, 0.5);
        return (float) Math.max(0.1, Math.min(3, factor));
    }

    /**
     * Feels like, in Celsius: the air and radiant heat, after clothing and fur, activity, wetness,
     * humidity and water. Clothing keeps heat in up to the middle of the comfortable range; warm
     * clothing adds a little in heat unless the coat sheds.
     */
    public static float feelsLike(float ambient, float humidity, float wetness, boolean immersed, float water,
                                  float activity, ThermalProtection clothing, ThermalProfile profile, ComfortRange range) {
        float warmthOfWork = profile.ectotherm() ? 0 : activity;
        if (immersed) return NEUTRAL_WATER - 13 + (water - NEUTRAL_WATER) * WATER_FACTOR + warmthOfWork;
        wetness = profile.soak(wetness);
        float retained = 1 - 0.75f * wetness;
        ThermalProtection protection = new ThermalProtection(0, clothing.coldResistance() * retained,
                clothing.heatResistance(), clothing.thermalResistance() * retained);
        float mid = range.mid();
        float air = protection.protectAmbient(ambient, mid);
        float feels = air;
        float warmth = (profile.insulation() + clothing.offset() * retained) / TemperatureData.AMBIENT_PULL_PER_DEGREE;
        if (warmth > 0) {
            if (air < mid) feels = Math.min(Math.max(air, mid), air + warmth);
            else if (!profile.sheds()) feels += warmth * 0.35f * Math.min(1f, (air - mid) / 6f);
        } else feels += warmth;
        feels += warmthOfWork;
        // Evaporation: wet skin and clothes chill toward the wet bulb.
        double wetBulb = Evaporation.wetBulb(ambient, humidity);
        // The gap to the wet bulb is capped: a fire's radiant heat raises the reading, not the air's thirst.
        if (wetness > 0) feels -= (float) (wetness * (2 + Math.min(MAX_EVAPORATIVE_CHILL, Math.max(0, ambient - wetBulb))));
        feels += humidityEffect(ambient, humidity);
        // Past the wet-bulb limit, sweat cannot cool a body at all.
        if (wetBulb > WET_BULB_LIMIT) feels += (float) ((wetBulb - WET_BULB_LIMIT) * 1.5);
        return feels;
    }

    /** Water vapour in mild air (20 C, half humid), in hPa: neither muggy nor dry. */
    static final double VAPOUR_REFERENCE = 0.5 * 6.105 * Math.exp(17.27 * 20 / (237.7 + 20));

    /**
     * Humid heat feels hotter and dry heat milder, as sweat fails or works: Steadman's 0.33 C per
     * hPa of water vapour against mild air. It counts the water actually in the air, which a fire's
     * radiant heat does not change, and fades out below 15 C.
     */
    static float humidityEffect(float air, float humidity) {
        float fade = Math.max(0, Math.min(1, (air - 15) / 5));
        if (fade == 0) return 0;
        return (float) (0.33 * (humidity * Evaporation.saturationPressure(air) - VAPOUR_REFERENCE) * fade);
    }

    /** Degrees past the comfortable range, signed, scaled by how strongly this body feels cold or heat. */
    public static float load(float feelsLike, ComfortRange range, ThermalProfile profile) {
        float excess = range.excess(feelsLike);
        float sensitivity = excess < 0 ? profile.cold() : profile.heat();
        return excess == 0 || sensitivity == 0 ? 0f : excess * sensitivity;
    }

    public static float load(float ambient, float humidity, float wetness, boolean immersed, float water, float activity,
                             ThermalProtection clothing, ThermalProfile profile, ComfortRange range) {
        return load(feelsLike(ambient, humidity, wetness, immersed, water, activity, clothing, profile, range), range, profile);
    }

    /** Relative humidity holding the reference vapour at this temperature: neither muggy nor dry. */
    public static float neutralHumidity(float air) {
        return (float) Math.max(0.05, Math.min(1, VAPOUR_REFERENCE / Evaporation.saturationPressure(air)));
    }

    /** Neutral humidity, water at the air's temperature, and Townstead's range moved by the Root's offset. */
    public static float load(float ambient, float wetness, boolean immersed, float activity,
                             ThermalProtection clothing, ThermalProfile profile) {
        return load(ambient, neutralHumidity(ambient), wetness, immersed, ambient, activity, clothing, profile,
                TemperatureSettings.get().comfortRange().offset(profile.comfortOffset()));
    }
}
