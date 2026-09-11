package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.compat.temperature.AmbientTemperatureBridge;
import com.aetherianartificer.townstead.compat.temperature.BuiltinTemperatureBridge;
import com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Rules for the temperature need. Body and ambient temperature are Celsius everywhere, stored as
 * integer tenths of a degree (370 = 37.0). Fahrenheit exists only at display time.
 */
public final class TemperatureData {

    public static final int ACCUMULATION_INTERVAL = 20;
    public static final int MOOD_CHECK_INTERVAL = 2400;
    public static final long AMBIENT_SAMPLE_TICKS = 200L;

    /** Fraction of the gap to the target body temperature closed per accumulation interval. */
    public static final float RATE = (float) (1 - Math.pow(0.75, ACCUMULATION_INTERVAL / 500d));
    /** Ambient reference: a body rests at its neutral in a 20 degree room. */
    public static final float AMBIENT_REFERENCE = 20f;
    public static final float AMBIENT_PULL_PER_DEGREE = 0.1f;
    public static final float ACTIVITY_WORK = 0.4f;
    public static final float ACTIVITY_MEET = 0f;
    public static final float ACTIVITY_IDLE = -0.2f;
    public static final float ACTIVITY_COMBAT = 0.6f;
    public static final float WETNESS_COLD = -0.8f;
    public static final float CLOTHING_CLAMP = 1.5f;
    /** Degrees above the reference over which worn clothing stops counting. */
    public static final float CLOTHING_FADE_DEGREES = 5f;
    public static final float SLEEP_COLD_FACTOR = 0.5f;
    public static final float ECTOTHERM_FOLLOW = 0.5f;

    public static final float DEFAULT_NEUTRAL = 37.0f;
    public static final float DEFAULT_BAND = 0.5f;
    public static final int MIN_BODY_TENTHS = 0;
    public static final int MAX_BODY_TENTHS = 600;

    private static final String KEY_BODY = "bodyTemp";
    private static final String KEY_AMBIENT = "ambient";
    private static final String KEY_WET = "wet";
    private static final String KEY_MOOD_DRIFT = "temperatureMoodDrift";
    private static final String KEY_CRISIS = "thermalCrisis";
    private static final String KEY_RELIEF_DEBUG = "reliefDebug";

    public static final String EDITOR_KEY_BODY_TEMPERATURE = "townstead_body_temperature";

    private TemperatureData() {}

    /** Display unit; the client config picks one and nothing else ever changes unit. */
    public enum Unit { CELSIUS, FAHRENHEIT }

    /** The tier a synced flag byte carries, for client readouts that have no gene profile. */
    public static Tier tierFromFlags(int flags) {
        int ordinal = TemperatureSyncPayload.tier(flags);
        Tier[] tiers = Tier.values();
        return ordinal >= 0 && ordinal < tiers.length ? tiers[ordinal] : Tier.COMFORTABLE;
    }

    public static int tenths(float celsius) {
        return Math.round(celsius * 10f);
    }

    /** Ambient in tenths rounded to the nearest half degree. */
    public static int quantiseAmbient(float celsius) {
        return Math.round(celsius * 2f) * 5;
    }

    public static float celsius(int tenths) {
        return tenths / 10f;
    }

    /** {@code 36.2 °C} or {@code 97.2 °F}; the number only ever changes unit at display time. */
    public static String format(int tenths, boolean fahrenheit) {
        float c = celsius(tenths);
        if (fahrenheit) {
            float f = c * 9f / 5f + 32f;
            return String.format(java.util.Locale.ROOT, "%.1f °F", f);
        }
        return String.format(java.util.Locale.ROOT, "%.1f °C", c);
    }

    /** Ambient Celsius at the position from the selected backend, falling back to the built-in model on NaN. */
    public static float ambientCelsius(ServerLevel level, BlockPos pos) {
        float ambient = unregulatedAmbientCelsius(level, pos);
        return com.aetherianartificer.townstead.compat.temperature.ToughAsNailsTemperatureBridge.INSTANCE
                .regulatedCelsius(level, pos, ambient);
    }

    private static float unregulatedAmbientCelsius(ServerLevel level, BlockPos pos) {
        var room = RoomHeat.at(level, pos);
        if (room.isPresent()) return (float) room.getAsDouble();
        AmbientTemperatureBridge bridge = TemperatureBridgeResolver.get();
        float value = bridge.ambientCelsius(level, pos);
        if (!Float.isFinite(value) && bridge != BuiltinTemperatureBridge.INSTANCE) {
            value = BuiltinTemperatureBridge.INSTANCE.ambientCelsius(level, pos);
        }
        return !Float.isFinite(value) ? AMBIENT_REFERENCE : value;
    }

    /** A thermometer reads stored air, without a person's directional radiant exposure. */
    public static float airCelsius(ServerLevel level, BlockPos pos) {
        var air = RoomHeat.airAt(level, pos);
        float value = air.isPresent() ? (float) air.getAsDouble()
                : com.aetherianartificer.townstead.compat.temperature.RoomHeatBackend.outdoor(level, pos);
        return com.aetherianartificer.townstead.compat.temperature.ToughAsNailsTemperatureBridge.INSTANCE
                .regulatedCelsius(level, pos, value);
    }

    /** The operative environment used by players and villagers, before personal modifiers. */
    public static float ambientCelsiusFor(ServerLevel level, net.conczin.mca.entity.VillagerEntityMCA villager) {
        return ambientCelsius(level, villager.blockPosition());
    }

    /** Rain on the skin or standing in water. Townstead's own check: no temperature mod tracks villagers. */
    public static boolean isWet(LivingEntity entity) {
        return entity.isInWaterOrRain();
    }

    public static Tier tier(int bodyTenths, ThermalProfile profile) {
        float distance = (celsius(bodyTenths) - profile.neutral()) / Math.max(0.05f, profile.band());
        int severity = Math.abs(distance) <= 1f ? 0 : Math.abs(distance) <= 3f ? 1 : Math.abs(distance) <= 6f ? 2 : 3;
        if (severity == 0) return Tier.COMFORTABLE;
        boolean cold = distance < 0;
        return switch (severity) {
            case 1 -> cold ? Tier.CHILLY : Tier.WARM;
            case 2 -> cold ? Tier.COLD : Tier.HOT;
            default -> cold ? Tier.FREEZING : Tier.SWELTERING;
        };
    }

    public static int getBodyTemp(CompoundTag tag) {
        return tag.contains(KEY_BODY) ? tag.getInt(KEY_BODY) : Integer.MIN_VALUE;
    }

    public static int getAmbient(CompoundTag tag) {
        return tag.contains(KEY_AMBIENT) ? tag.getInt(KEY_AMBIENT) : tenths(AMBIENT_REFERENCE);
    }

    public static boolean isWet(CompoundTag tag) {
        return tag.getBoolean(KEY_WET);
    }

    public static float getMoodDrift(CompoundTag tag) {
        return tag.getFloat(KEY_MOOD_DRIFT);
    }

    public static boolean isCrisis(CompoundTag tag) {
        return tag.getBoolean(KEY_CRISIS);
    }

    public static String getReliefDebug(CompoundTag tag) {
        return tag.contains(KEY_RELIEF_DEBUG) ? tag.getString(KEY_RELIEF_DEBUG) : "none";
    }

    public static void write(CompoundTag tag, int bodyTenths, int ambientTenths, boolean wet, float moodDrift,
                             boolean crisis, String reliefDebug) {
        if (bodyTenths != Integer.MIN_VALUE) tag.putInt(KEY_BODY, bodyTenths);
        tag.putInt(KEY_AMBIENT, ambientTenths);
        tag.putBoolean(KEY_WET, wet);
        tag.putFloat(KEY_MOOD_DRIFT, moodDrift);
        tag.putBoolean(KEY_CRISIS, crisis);
        tag.putString(KEY_RELIEF_DEBUG, reliefDebug == null ? "none" : reliefDebug);
    }

    /**
     * Distance from neutral in bands, on the {@code FatigueData} pattern: comfortable within one
     * band, chilly or warm within three, cold or hot within six, crisis beyond.
     */
    public enum Tier {
        FREEZING("townstead.temperature.freezing", 0x55AAFF, -0.75f, -0.25, 3, true),
        COLD("townstead.temperature.cold", 0x7FC8FF, -0.5f, -0.10, 2, true),
        CHILLY("townstead.temperature.chilly", 0xBFE3FF, -0.2f, 0.0, 1, true),
        COMFORTABLE("townstead.temperature.comfortable", 0xFFFFFF, 0.15f, 0.0, 0, false),
        WARM("townstead.temperature.warm", 0xFFD9A0, -0.2f, 0.0, 1, false),
        HOT("townstead.temperature.hot", 0xFFAA55, -0.5f, -0.10, 2, false),
        SWELTERING("townstead.temperature.sweltering", 0xFF5555, -0.75f, -0.25, 3, false);

        private final String translationKey;
        private final int color;
        private final float moodPressure;
        private final double speedPenalty;
        private final int severity;
        private final boolean cold;

        Tier(String translationKey, int color, float moodPressure, double speedPenalty, int severity, boolean cold) {
            this.translationKey = translationKey;
            this.color = color;
            this.moodPressure = moodPressure;
            this.speedPenalty = speedPenalty;
            this.severity = severity;
            this.cold = cold;
        }

        public String getTranslationKey() { return translationKey; }
        public int getColor() { return color; }
        public float moodPressure() { return moodPressure; }
        public double speedPenalty() { return speedPenalty; }
        /** 0 comfortable, 1 mild, 2 needs relief off duty, 3 crisis. */
        public int severity() { return severity; }
        public boolean isCold() { return cold; }
        public boolean isCrisis() { return severity == 3; }
        public boolean wantsRelief() { return severity >= 2; }
    }
}
