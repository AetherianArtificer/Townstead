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
    /**
     * Whether Townstead applies heat and cooling blocks to every body, players included. It does
     * when its climate and room heat both run; otherwise each temperature mod keeps its own block
     * heat for players.
     */
    public static boolean ownsBlockHeat() {
        return TemperatureSettings.get().roomHeatEnabled() && TownsteadConfig.townsteadClimateAuthority()
                && TownsteadConfig.temperatureActive();
    }
    /**
     * A player's environment for the temperature mods: room air or open-air climate, plus radiant
     * heat from sources in sight when Townstead owns block heat. Without it, the mod adds its own.
     */
    public static OptionalDouble environment(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || bypassed() || !TownsteadConfig.temperatureActive()
                || !TemperatureSettings.get().roomHeatEnabled() && !TownsteadConfig.townsteadClimateAuthority())
            return OptionalDouble.empty();
        boolean owns = ownsBlockHeat();
        return OptionalDouble.of((owns ? ENVIRONMENT : CLIMATE_ONLY).get(server, pos.asLong(), server.getGameTime(), () -> {
            var room = owns ? RoomHeat.at(server, pos) : RoomHeat.airAt(server, pos);
            if (room.isPresent()) return room.getAsDouble();
            float outside = outdoor(server, pos);
            return owns ? (double) outside + ThermalBlocks.sourceOffset(server, pos, TemperatureSettings.get(), outside)
                    : (double) outside;
        }));
    }
    private static final ThermalCache<ServerLevel, Long, Double> CLIMATE_ONLY = new ThermalCache<>(1, 2048);
    // Player temperature updates ask every tick; the climate at a block changes over seconds.
    private static final ThermalCache<ServerLevel, Long, Float> COLD_SWEAT_SHIFT = new ThermalCache<>(20, 2048);
    private record TanReading(float climate, float shift) {}
    private static final ThermalCache<ServerLevel, Long, TanReading> TAN_SHIFT = new ThermalCache<>(20, 2048);
    public static void clear() { ENVIRONMENT.clear(); CLIMATE_ONLY.clear(); COLD_SWEAT_SHIFT.clear(); TAN_SHIFT.clear(); }
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
        if (!TownsteadConfig.temperatureActive()) return 0f;
        return COLD_SWEAT_SHIFT.get(level, pos.asLong(), level.getGameTime(), () -> computeColdSweatShift(level, pos.immutable()));
    }
    private static float computeColdSweatShift(ServerLevel level, BlockPos pos) {
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
        if (!TownsteadConfig.temperatureActive()) return null;
        int full = java.util.Arrays.asList(TAN_LEVELS).indexOf(fullLevel);
        if (full < 0) return null;
        TanReading reading = TAN_SHIFT.get(level, pos.asLong(), level.getGameTime(), () -> {
            BlockPos at = pos.immutable();
            float climate = ToughAsNailsTemperatureBridge.INSTANCE.outdoorCelsius(level, at);
            if (!Float.isFinite(climate)) return null;
            float shift = TownsteadConfig.townsteadClimateAuthority()
                    ? builtinOutdoor(level, at) - climate
                    : GroundClimate.backendShift(level, at, ToughAsNailsTemperatureBridge.INSTANCE, () -> climate);
            return new TanReading(climate, shift);
        });
        if (reading == null || reading.shift() == 0f) return null;
        int shifted = TanTemperaturePolicy.shiftedIndex(reading.climate(), reading.shift(), full - tanOrdinal(reading.climate()));
        return shifted == full ? null : TAN_LEVELS[shifted];
    }
    /**
     * Room air replaces Cold Sweat's climate; Cold Sweat keeps its own block heat, and its hearth
     * blends from the room air rather than from the outdoor climate.
     */
    /** Cold Sweat's temperature traits for an entity: WORLD from Townstead's environment, RATE without its clothing. */
    public static double coldSweatEntity(net.minecraft.world.entity.LivingEntity entity, java.util.List<?> modifiers,
                                         Object trait, double original) {
        if (!TownsteadConfig.temperatureActive()) return original;
        String name = trait instanceof Enum<?> value ? value.name() : "";
        if (name.equals("WORLD")) return coldSweatPlayer(entity, modifiers, trait, original);
        // Clothing changes what the player feels, so Cold Sweat's insulation no longer slows the body as well.
        if (!(entity instanceof net.minecraft.world.entity.player.Player player)
                || !com.aetherianartificer.townstead.temperature.PlayerFeelsLike.active() || bypassed()) return original;
        if (name.equals("RATE"))
            return original - ColdSweatTemperatureBridge.INSTANCE.contribution(modifiers, trait, "ArmorInsulationTempModifier");
        // A Root with its own acclimation limits acclimates by Townstead's rule instead of Cold Sweat's.
        if ((name.equals("FREEZING_POINT") || name.equals("BURNING_POINT"))
                && com.aetherianartificer.townstead.temperature.ThermalProfile.of(player).acclimation() != null)
            return original - ColdSweatTemperatureBridge.INSTANCE.contribution(modifiers, trait, "AcclimationTempModifier");
        return original;
    }
    public static double coldSweatPlayer(net.minecraft.world.entity.LivingEntity entity, java.util.List<?> modifiers, Object trait, double original) {
        Level level = entity.level();
        BlockPos pos = entity.blockPosition();
        if (!(level instanceof ServerLevel server) || bypassed()) return original;
        var bridge = ColdSweatTemperatureBridge.INSTANCE;
        if (ownsBlockHeat()) {
            // Townstead's air and radiant heat replace Cold Sweat's climate and its own block heat.
            OptionalDouble environment = environment(server, pos);
            float climate = sample(1, () -> bridge.uncachedAmbient(server, pos, true));
            if (environment.isEmpty() || !Float.isFinite(climate)) return original;
            double blocks = bridge.contribution(modifiers, trait, "BlockTempModifier");
            if (entity instanceof net.minecraft.world.entity.player.Player player) {
                // What the player feels replaces the air: clothing, wetness, humidity and Root traits.
                double soak = bridge.contribution(modifiers, trait, "WaterTempModifier");
                float felt = com.aetherianartificer.townstead.temperature.PlayerFeelsLike.sent(player,
                        (float) environment.getAsDouble(), bridge.wetness(modifiers, player));
                return bridge.shiftBeforeThermalSources(modifiers, trait, original, (felt - climate) / 25.0 - blocks - soak);
            }
            return bridge.shiftBeforeThermalSources(modifiers, trait, original,
                    (environment.getAsDouble() - climate) / 25.0 - blocks);
        }
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
