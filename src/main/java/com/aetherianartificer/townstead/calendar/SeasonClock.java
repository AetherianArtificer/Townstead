package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.calendar.CalendarCompat;
import com.aetherianartificer.townstead.compat.calendar.bridge.EclipticBridge;
import com.aetherianartificer.townstead.compat.calendar.bridge.SereneBridge;
import com.aetherianartificer.townstead.compat.calendar.bridge.TfcBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Where the world is in its seasonal year, read only from a season mod. Phase runs 0 to 1 from
 * the start of the provider's spring, so the summer solstice sits at 0.375 and the winter solstice
 * at 0.875. With no season mod there is no clock, and climate stays at its yearly mean.
 *
 * <p>The display calendar is separate: any profile can label days, but only this clock decides
 * the season.</p>
 */
public final class SeasonClock {
    /** One reading: the provider, the year phase, the provider's year length in days, its season. */
    public record State(String provider, double phase, int yearDays, @Nullable Season season) {}

    private SeasonClock() {}

    public static Optional<State> current(MinecraftServer server) {
        return server == null ? Optional.empty() : current(server.overworld());
    }

    private static java.lang.ref.WeakReference<ServerLevel> cachedLevel = new java.lang.ref.WeakReference<>(null);
    private static long cachedTick = Long.MIN_VALUE;
    private static Optional<State> cached = Optional.empty();

    /** TFC, then Ecliptic, then Serene; the Serene API stub is Ecliptic in disguise and is skipped. Read once per tick. */
    public static synchronized Optional<State> current(ServerLevel level) {
        if (level == null) return Optional.empty();
        long tick = level.getGameTime();
        if (cachedLevel.get() == level && cachedTick == tick) return cached;
        cached = read(level);
        cachedLevel = new java.lang.ref.WeakReference<>(level);
        cachedTick = tick;
        return cached;
    }

    private static Optional<State> read(ServerLevel level) {
        double dayFraction = Math.floorMod(level.getDayTime(), 24000L) / 24000.0;
        if (ModCompat.isLoaded(CalendarCompat.TFC_MOD_ID)) {
            var tfc = TfcBridge.currentState(level);
            if (tfc.isPresent()) {
                int year = 12 * tfc.get().daysInMonth();
                long fromSpring = tfc.get().absDay() - (long) TfcBridge.springStartMonth() * tfc.get().daysInMonth();
                return Optional.of(new State("tfc", cyclePhase(fromSpring, dayFraction, year), year, tfc.get().season()));
            }
        }
        if (ModCompat.isLoaded(CalendarCompat.ECLIPTIC_MOD_ID)) {
            var ecliptic = EclipticBridge.currentState(level);
            if (ecliptic.isPresent() && ecliptic.get().season() != null) {
                int year = 24 * ecliptic.get().termLength();
                return Optional.of(new State("ecliptic",
                        cyclePhase(ecliptic.get().absDay(), dayFraction, year), year, ecliptic.get().season()));
            }
        }
        if (ModCompat.isLoaded(CalendarCompat.SERENE_MOD_ID) && !CalendarCompat.isSereneApiStub()) {
            var serene = SereneBridge.currentState(level);
            var phase = SereneBridge.phase(level);
            if (serene.isPresent() && phase.isPresent()) {
                return Optional.of(new State("serene", phase.getAsDouble(), serene.get().daysPerCycle(), serene.get().season()));
            }
        }
        return Optional.empty();
    }

    /** Today's season, or null when no season mod drives one. */
    @Nullable
    public static Season season(MinecraftServer server) {
        return current(server).map(State::season).orElse(null);
    }

    /** Phase of a day count within a cycle, plus the fraction of the current day. */
    static double cyclePhase(long day, double dayFraction, int yearDays) {
        if (yearDays <= 0) return 0;
        return (Math.floorMod(day, (long) yearDays) + Math.max(0, Math.min(1, dayFraction))) / yearDays;
    }
}
