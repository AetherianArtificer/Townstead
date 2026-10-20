package com.aetherianartificer.townstead.climate;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.SeasonClock;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Wet and dry seasons for {@code wet_dry} zones, from the season mod. Serene Seasons runs a
 * tropical year of six two-sub-season steps, wettest at the turn of the year and driest at mid
 * year. Ecliptic Seasons' monsoon rain chance per solar term is wet from early summer to the first
 * frost and dry otherwise. {@code wetness} runs from 0 at the driest point to 1 at the wettest.
 */
public final class WetDry {
    public record Phase(boolean wet, float wetness) {}

    private static boolean monsoonLoaded;
    @Nullable private static float[] monsoon;

    private WetDry() {}

    /** The wet/dry phase at a year phase of the clock's provider; empty when the provider has none. */
    public static Optional<Phase> at(@Nullable SeasonClock.State clock, double phase) {
        if (clock == null) return Optional.empty();
        return switch (clock.provider()) {
            case "serene" -> Optional.of(serene(phase));
            case "ecliptic" -> {
                float[] rain = monsoonRain();
                yield rain == null ? Optional.empty() : Optional.of(monsoon(phase, rain));
            }
            default -> Optional.empty();
        };
    }

    public static Optional<Phase> current(@Nullable SeasonClock.State clock) {
        return clock == null ? Optional.empty() : at(clock, clock.phase());
    }

    /** The zone's dry offset at wetness 0, its wet offset at 1, linear between. */
    static float offset(float dry, float wet, float wetness) {
        return dry + (wet - dry) * wetness;
    }

    /** Serene's tropical season: its sub-season index maps to six steps, the last three wet. */
    static Phase serene(double phase) {
        double p = phase - Math.floor(phase);
        int sub = Math.min(11, (int) Math.floor(p * 12));
        int tropical = ((sub + 11) / 2 + 5) % 6;
        return new Phase(tropical >= 3, (float) ((1 + Math.cos(2 * Math.PI * p)) / 2));
    }

    /** Ecliptic's monsoon: the term's rain chance decides wet, the chance relative to its peak sets wetness. */
    static Phase monsoon(double phase, float[] rain) {
        int n = rain.length;
        double p = (phase - Math.floor(phase)) * n;
        int term = Math.min(n - 1, (int) Math.floor(p));
        float max = 0f;
        for (float r : rain) max = Math.max(max, r);
        if (max <= 0f) return new Phase(false, 0f);
        float here = rain[term], next = rain[(term + 1) % n];
        float chance = here + (next - here) * (float) (p - term);
        return new Phase(here > 0f, Math.max(0f, Math.min(1f, chance / max)));
    }

    @Nullable
    private static synchronized float[] monsoonRain() {
        if (monsoonLoaded) return monsoon;
        monsoonLoaded = true;
        try {
            Class<?> cls = Class.forName("com.teamtea.eclipticseasons.api.constant.climate.MonsoonRain");
            var chance = cls.getMethod("getRainChance");
            Object[] terms = cls.getEnumConstants();
            float[] rain = new float[24];
            for (int i = 0; i < 24 && i < terms.length; i++) rain[i] = ((Number) chance.invoke(terms[i])).floatValue();
            monsoon = rain;
        } catch (Throwable t) {
            Townstead.LOGGER.info("[Climate] Ecliptic monsoon rain unavailable: {}", t.toString());
        }
        return monsoon;
    }
}
