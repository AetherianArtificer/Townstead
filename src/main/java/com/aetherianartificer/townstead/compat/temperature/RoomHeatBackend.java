package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.temperature.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/** Recursion-safe boundary between the shared simulation and backend environmental queries. */
public final class RoomHeatBackend {
    private static final ThreadLocal<Integer> MODE = ThreadLocal.withInitial(() -> 0);
    private static final ThermalCache<ServerLevel, Long, Double> ENVIRONMENT = new ThermalCache<>(1, 2048);
    private RoomHeatBackend() {}
    public static boolean bypassed() { return MODE.get() != 0; }
    public static boolean outdoorSampling() { return MODE.get() == 2; }
    private static <T> T sample(int mode, Supplier<T> supplier) {
        int previous = MODE.get(); MODE.set(mode);
        try { return supplier.get(); } finally { MODE.set(previous); }
    }
    public static OptionalDouble room(Level level, BlockPos pos) {
        return level instanceof ServerLevel server && !bypassed() ? RoomHeat.at(server, pos) : OptionalDouble.empty();
    }
    /** LSO players and world queries share the same climate and local source exposure. */
    public static OptionalDouble environment(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || bypassed()
                || !TemperatureSettings.get().roomHeatEnabled() && !TownsteadConfig.townsteadClimateAuthority())
            return OptionalDouble.empty();
        return OptionalDouble.of(ENVIRONMENT.get(server, pos.asLong(), server.getGameTime(), () -> {
            var room = RoomHeat.at(server, pos);
            return room.isPresent() ? room.getAsDouble() : (double) outdoor(server, pos)
                    + ThermalBlocks.sourceOffset(server, pos, TemperatureSettings.get());
        }));
    }
    public static void clear() { ENVIRONMENT.clear(); }
    public static float outdoor(ServerLevel level, BlockPos pos) {
        return sample(2, () -> {
            var backend = TemperatureBridgeResolver.get();
            if (backend == BuiltinTemperatureBridge.INSTANCE || TownsteadConfig.townsteadClimateAuthority())
                return builtinOutdoor(level, pos);
            float value;
            if (backend == ColdSweatTemperatureBridge.INSTANCE) value = ColdSweatTemperatureBridge.INSTANCE.uncachedAmbient(level, pos, true);
            else if (backend == ToughAsNailsTemperatureBridge.INSTANCE) value = ToughAsNailsTemperatureBridge.INSTANCE.outdoorCelsius(level, pos);
            else value = backend.ambientCelsius(level, pos);
            if (!Float.isFinite(value)) return builtinOutdoor(level, pos);
            return value + GroundClimate.backendShift(level, pos, backend, () -> value);
        });
    }
    private static float builtinOutdoor(ServerLevel level, BlockPos pos) {
        return BuiltinTemperatureBridge.climateCelsius(level, pos);
    }
    /**
     * How far to move Cold Sweat's open-air climate at a position outside any room: onto
     * Townstead's climate under Townstead authority, otherwise by the seasons, snow and rock heat
     * its own model lacks. Zero when nothing applies.
     */
    public static float coldSweatShift(ServerLevel level, BlockPos pos) {
        java.util.function.Supplier<Float> climate =
                () -> sample(1, () -> ColdSweatTemperatureBridge.INSTANCE.uncachedAmbient(level, pos, true));
        if (TownsteadConfig.townsteadClimateAuthority()) {
            // Replace Cold Sweat's biome, depth, weather and season climate with Townstead's.
            Float own = climate.get();
            return own == null || !Float.isFinite(own) ? 0f : builtinOutdoor(level, pos) - own;
        }
        return GroundClimate.backendShift(level, pos, ColdSweatTemperatureBridge.INSTANCE, climate);
    }
    private static final String[] TAN_LEVELS = {"ICY", "COLD", "NEUTRAL", "WARM", "HOT"};
    /**
     * TaN's level outside any room: Townstead's climate under Townstead authority, otherwise TaN's
     * own plus what it lacks. TaN's proximity steps for nearby blocks are kept; null when nothing
     * changes.
     */
    public static String tanOutdoor(ServerLevel level, BlockPos pos, String fullLevel) {
        int full = java.util.Arrays.asList(TAN_LEVELS).indexOf(fullLevel);
        if (full < 0) return null;
        float climate = ToughAsNailsTemperatureBridge.INSTANCE.outdoorCelsius(level, pos);
        if (!Float.isFinite(climate)) return null;
        float shift = TownsteadConfig.townsteadClimateAuthority()
                ? builtinOutdoor(level, pos) - climate
                : GroundClimate.backendShift(level, pos, ToughAsNailsTemperatureBridge.INSTANCE, () -> climate);
        if (shift == 0f) return null;
        int shifted = TanTemperaturePolicy.shiftedIndex(climate, shift, full - tanOrdinal(climate));
        return shifted == full ? null : TAN_LEVELS[shifted];
    }
    /**
     * Room air replaces Cold Sweat's climate; Cold Sweat keeps its own block heat, and its hearth
     * blends from the room air rather than from the outdoor climate.
     */
    public static double coldSweatPlayer(Level level, BlockPos pos, java.util.List<?> modifiers, Object trait, double original) {
        if (!(level instanceof ServerLevel server) || bypassed()) return original;
        OptionalDouble air = RoomHeat.airAt(server, pos);
        if (air.isEmpty()) {
            float shift = coldSweatShift(server, pos);
            return shift == 0f ? original
                    : ColdSweatTemperatureBridge.INSTANCE.shiftBeforeThermalSources(modifiers, trait, original, shift / 25.0);
        }
        float climate = sample(1, () -> ColdSweatTemperatureBridge.INSTANCE.uncachedAmbient(server, pos, true));
        if (!Float.isFinite(climate)) return original;
        return ColdSweatTemperatureBridge.INSTANCE.shiftBeforeThermalSources(modifiers, trait, original,
                (air.getAsDouble() - climate) / 25.0);
    }
    public static int tanOrdinal(double celsius) {
        if (celsius < -2.5) return 0;
        if (celsius < 12.5) return 1;
        if (celsius < 25) return 2;
        if (celsius < 35) return 3;
        return 4;
    }
}
