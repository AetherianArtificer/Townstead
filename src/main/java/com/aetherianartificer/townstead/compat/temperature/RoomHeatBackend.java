package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.temperature.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/** Recursion-safe boundary between the shared simulation and backend environmental queries. */
public final class RoomHeatBackend {
    private static final ThreadLocal<Integer> MODE = ThreadLocal.withInitial(() -> 0);
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
    public static float outdoor(ServerLevel level, BlockPos pos) {
        return sample(2, () -> {
            var backend = TemperatureBridgeResolver.get();
            float value;
            if (backend == ColdSweatTemperatureBridge.INSTANCE) value = ColdSweatTemperatureBridge.INSTANCE.uncachedAmbient(level, pos, true);
            else if (backend == ToughAsNailsTemperatureBridge.INSTANCE) value = ToughAsNailsTemperatureBridge.INSTANCE.outdoorCelsius(level, pos);
            else if (backend == BuiltinTemperatureBridge.INSTANCE) value = builtinOutdoor(level, pos);
            else value = backend.ambientCelsius(level, pos);
            return Float.isFinite(value) ? value : builtinOutdoor(level, pos);
        });
    }
    private static float builtinOutdoor(ServerLevel level, BlockPos pos) {
        var settings = TemperatureSettings.get();
        float value = BuiltinTemperatureBridge.baseCelsius(level, pos);
        if (level.isNight()) value += settings.nightOffset();
        if (level.isRainingAt(pos)) value += settings.rainOffset();
        return value;
    }
    public static double coldSweatPlayer(Level level, BlockPos pos, double original) {
        OptionalDouble room = room(level, pos);
        if (room.isEmpty() || !(level instanceof ServerLevel server)) return original;
        float baseline = sample(1, () -> ColdSweatTemperatureBridge.INSTANCE.uncachedAmbient(server, pos, false));
        return Float.isFinite(baseline) ? original + (room.getAsDouble() - baseline) / 25.0 : original;
    }
    public static int tanOrdinal(double celsius) {
        if (celsius < -2.5) return 0;
        if (celsius < 12.5) return 1;
        if (celsius < 25) return 2;
        if (celsius < 35) return 3;
        return 4;
    }
}
