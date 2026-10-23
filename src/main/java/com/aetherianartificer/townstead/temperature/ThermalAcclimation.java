package com.aetherianartificer.townstead.temperature;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * A body's adaptation to the climate it lives in, from -1 (cold-adapted) to +1 (hot-adapted). Full
 * cold adaptation moves both ends of the comfortable range down; full heat adaptation moves them up.
 */
public final class ThermalAcclimation {
    private ThermalAcclimation() {}

    /**
     * How far full adaptation moves the range's ends, and a speed multiplier. NaN keeps the active
     * temperature mod's or Townstead's value; a speed of 0 turns acclimation off.
     */
    public record Limits(float coldMin, float coldMax, float hotMin, float hotMax, float speed) {
        public static final Limits OFF = new Limits(Float.NaN, Float.NaN, Float.NaN, Float.NaN, 0f);

        public boolean off() { return speed == 0f; }

        /** These limits over a fallback, field by field. */
        public Limits over(Limits fallback) {
            return new Limits(pick(coldMin, fallback.coldMin), pick(coldMax, fallback.coldMax),
                    pick(hotMin, fallback.hotMin), pick(hotMax, fallback.hotMax), pick(speed, fallback.speed));
        }

        private static float pick(float value, float fallback) {
            return Float.isNaN(value) ? fallback : value;
        }

        /**
         * {@code "acclimation": false}, or an object with {@code cold_shift_c} and {@code hot_shift_c}
         * as [min, max] and {@code speed}. Null when absent.
         */
        public static @Nullable Limits parse(@Nullable JsonElement json) {
            if (json == null || json.isJsonNull()) return null;
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean())
                return json.getAsBoolean() ? new Limits(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN) : OFF;
            JsonObject object = json.getAsJsonObject();
            float[] cold = pair(object, "cold_shift_c"), hot = pair(object, "hot_shift_c");
            return new Limits(cold[0], cold[1], hot[0], hot[1],
                    object.has("speed") ? Math.max(0f, GsonHelper.getAsFloat(object, "speed")) : Float.NaN);
        }

        private static float[] pair(JsonObject object, String key) {
            if (!object.has(key)) return new float[]{Float.NaN, Float.NaN};
            var array = GsonHelper.getAsJsonArray(object, key);
            return new float[]{array.get(0).getAsFloat(), array.size() > 1 ? array.get(1).getAsFloat() : array.get(0).getAsFloat()};
        }
    }

    /** Yearly mean of the plains the scale centres on: a body born there starts unadapted. */
    static final float TEMPERATE_MEAN = 13f, SEED_SPAN = 12f;
    private static final String PLAYER_KEY = "townstead_acclimation";

    /** Where acclimation heads: the side of the range's middle that feels like falls on, -1 to 1. */
    public static float target(float feelsLike, ComfortRange range) {
        float half = Math.max(0.5f, (range.high() - range.low()) / 2f);
        return Math.max(-1f, Math.min(1f, (feelsLike - range.mid()) / half));
    }

    /** One step toward the target, at {@code ratePerMinute} of a full change, times the limits' speed. */
    public static float step(float current, float target, float ratePerMinute, Limits limits, float minutes) {
        if (limits == null || limits.off() || minutes <= 0) return current;
        float speed = Float.isNaN(limits.speed()) ? 1f : limits.speed();
        float delta = Math.max(0f, ratePerMinute * speed * minutes);
        float gap = target - current;
        return Math.abs(gap) <= delta ? target : current + Math.signum(gap) * delta;
    }

    /** A new body starts adapted to the climate it is born in, from that place's yearly mean. */
    public static float seed(float yearlyMean) {
        return Math.max(-1f, Math.min(1f, (yearlyMean - TEMPERATE_MEAN) / SEED_SPAN));
    }

    /** Share of a full acclimation per real minute: the active temperature mod's, else Townstead's. */
    public static float ratePerMinute() {
        try {
            var bridge = com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver.get();
            float own = bridge == null ? Float.NaN : bridge.acclimationRatePerMinute();
            if (Float.isFinite(own) && own > 0) return own;
        } catch (RuntimeException | LinkageError ignored) {
        }
        return TemperatureSettings.get().acclimationRatePerMinute();
    }

    /**
     * Whether Townstead keeps this player's acclimation: always, unless Cold Sweat runs its own and
     * the player's Root sets no limits of its own.
     */
    public static boolean townsteadOwns(net.minecraft.world.entity.player.Player player, ThermalProfile profile) {
        var bridge = com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver.get();
        return bridge != com.aetherianartificer.townstead.compat.temperature.ColdSweatTemperatureBridge.INSTANCE
                || profile.acclimation() != null;
    }

    public static float player(net.minecraft.world.entity.player.Player player) {
        var data = player.getPersistentData();
        return data.contains(PLAYER_KEY) ? Math.max(-1f, Math.min(1f, data.getFloat(PLAYER_KEY))) : Float.NaN;
    }

    public static void setPlayer(net.minecraft.world.entity.player.Player player, float value) {
        player.getPersistentData().putFloat(PLAYER_KEY, Math.max(-1f, Math.min(1f, value)));
    }

    /** The comfortable range moved by an acclimation value from -1 to 1. */
    public static ComfortRange shift(ComfortRange range, float acclimation, Limits limits) {
        if (limits == null || limits.off() || acclimation == 0f) return range;
        float a = Math.max(-1f, Math.min(1f, acclimation));
        if (a < 0) return new ComfortRange(range.low() - a * limits.coldMin(), range.high() - a * limits.coldMax());
        return new ComfortRange(range.low() + a * limits.hotMin(), range.high() + a * limits.hotMax());
    }
}
