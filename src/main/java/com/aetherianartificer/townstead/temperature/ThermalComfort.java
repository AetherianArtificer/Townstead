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
    /** Degrees of fur or clothing warmth that make one clo: a tagged warm piece is one clo. */
    static final float WARMTH_PER_CLO = 5f;
    /** Everyday clothes every body wears, in clo; part of the resting reference. */
    static final float EVERYDAY_CLO = 0.5f;
    /** Still air around a resting body, in clo. Moving through air thins it. */
    static final float STILL_AIR_CLO = 0.75f;
    /** Share of heat lost by breath and bare skin, which clothing does not stop. */
    static final float UNCOVERED_SHARE = 0.2f;
    /** Skin above the middle of the comfortable range: where dry heat stops flowing out. */
    static final float SKIN_MARGIN = 9f;
    /** Seconds for exertion to build toward a harder activity, and to fade toward an easier one. */
    static final float EXERTION_RISE_SECONDS = 40f, EXERTION_FALL_SECONDS = 60f;

    /**
     * Heat a body makes at an activity (rest 0, walking 2, working 4, running or fighting 6), as a
     * multiple of rest. ISO 8996 rates: walking 2.4, moderate work 3.3, jogging 4.
     */
    public static float metabolism(float activity) {
        float a = Math.max(0, Math.min(6, activity));
        if (a <= 2) return 1 + 0.7f * a;
        if (a <= 4) return 2.4f + 0.45f * (a - 2);
        return 3.3f + 0.35f * (a - 4);
    }

    /**
     * Exertion moves toward the activity's metabolism: most of the way in about two minutes when
     * work starts, and back down in about three when it stops. A short sprint barely counts.
     */
    public static float exertion(float previous, float activity, float seconds) {
        float target = metabolism(activity);
        if (!(previous >= 1)) previous = 1;
        float tau = target > previous ? EXERTION_RISE_SECONDS : EXERTION_FALL_SECONDS;
        return previous + (target - previous) * (float) (1 - Math.exp(-Math.max(0, seconds) / tau));
    }

    /**
     * How much harder the cold works on this body than on the resting reference in everyday
     * clothes: exertion over heat loss (ISO 11079 heat balance). Clothing slows loss through the
     * covered skin; moving through air thins the still-air layer.
     */
    static float coldFactor(float exertion, float clo) {
        float m = Math.max(1, exertion);
        float base = EVERYDAY_CLO + STILL_AIR_CLO / (float) Math.pow(m, 0.75);
        float rest = EVERYDAY_CLO + STILL_AIR_CLO;
        float loss = (1 - UNCOVERED_SHARE) * rest / (base + Math.max(0, clo)) + UNCOVERED_SHARE * rest / base;
        return m / loss;
    }

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
     * Feels like, in Celsius: the air and radiant heat, after clothing and fur, exertion, wetness,
     * humidity and water, as the resting reference in everyday clothes would feel it. Below skin
     * temperature, heat leaves through the clothing, so insulation and exertion divide the gap to
     * the skin; in heat, both add warmth the body has to sweat away.
     *
     * @param exertion heat the body makes as a multiple of rest ({@link #exertion}); 1 at rest
     */
    public static float feelsLike(float ambient, float humidity, float wetness, boolean immersed, float water,
                                  float exertion, ThermalProtection clothing, ThermalProfile profile, ComfortRange range) {
        float m = profile.ectotherm() ? 1 : Math.max(1, exertion);
        if (immersed) return NEUTRAL_WATER - 13 + (water - NEUTRAL_WATER) * WATER_FACTOR + SKIN_MARGIN * (1 - 1 / m);
        wetness = profile.soak(wetness);
        float retained = 1 - 0.75f * wetness;
        float mid = range.mid();
        float clothingWarmth = clothing.offset() * retained / TemperatureData.AMBIENT_PULL_PER_DEGREE;
        float fur = Math.max(0, profile.insulation() / TemperatureData.AMBIENT_PULL_PER_DEGREE) / WARMTH_PER_CLO;
        // Immunity effects are not coats: they hold their side at the middle of the range.
        boolean coldImmune = clothing.coldResistance() + clothing.thermalResistance() >= ThermalProtection.IMMUNE;
        boolean heatImmune = clothing.heatResistance() + clothing.thermalResistance() >= ThermalProtection.IMMUNE;
        float worn = (coldImmune ? 0 : clothing.coldClo() * retained) + Math.max(0, clothingWarmth) / WARMTH_PER_CLO;
        float feels;
        if (ambient < mid) {
            float skin = mid + SKIN_MARGIN;
            feels = skin - (skin - ambient) / coldFactor(m, worn + fur);
            if (coldImmune) feels = Math.max(mid, feels);
        } else {
            if (heatImmune) return mid;
            float warmth = SKIN_MARGIN * (1 - 1 / coldFactor(m, worn + (profile.sheds() ? 0 : fur)));
            feels = mid + (ambient - mid + warmth) / (1 + clothing.heatClo());
        }
        if (clothingWarmth < 0) feels += clothingWarmth;
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

    public static float load(float ambient, float humidity, float wetness, boolean immersed, float water, float exertion,
                             ThermalProtection clothing, ThermalProfile profile, ComfortRange range) {
        return load(feelsLike(ambient, humidity, wetness, immersed, water, exertion, clothing, profile, range), range, profile);
    }

    /** Relative humidity holding the reference vapour at this temperature: neither muggy nor dry. */
    public static float neutralHumidity(float air) {
        return (float) Math.max(0.05, Math.min(1, VAPOUR_REFERENCE / Evaporation.saturationPressure(air)));
    }

    /** Neutral humidity, water at the air's temperature, and Townstead's range moved by the Root's offset. */
    public static float load(float ambient, float wetness, boolean immersed, float exertion,
                             ThermalProtection clothing, ThermalProfile profile) {
        return load(ambient, neutralHumidity(ambient), wetness, immersed, ambient, exertion, clothing, profile,
                TemperatureSettings.get().comfortRange().offset(profile.comfortOffset()));
    }
}
