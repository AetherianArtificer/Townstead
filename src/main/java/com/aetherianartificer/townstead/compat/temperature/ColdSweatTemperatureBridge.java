package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import com.aetherianartificer.townstead.temperature.ThermalCache;

import java.lang.reflect.Method;

/**
 * Cold Sweat's positional world temperature, converted from its Minecraft units to Celsius through
 * the mod's own {@code Temperature.convert} when it resolves, else by the mod's published scale
 * (one Minecraft unit is 25 degrees Celsius). Exact positions are cached for one second per
 * world instance. Pure reflection keeps Cold Sweat off the compile classpath.
 */
public final class ColdSweatTemperatureBridge implements AmbientTemperatureBridge {
    public static final ColdSweatTemperatureBridge INSTANCE = new ColdSweatTemperatureBridge();

    private static final double MC_UNIT_TO_CELSIUS = 25.0;

    private boolean initialized;
    private boolean active;
    private Method getTemperatureAt;
    private Method convert;
    private Object unitsMc;
    private Object unitsC;
    private Method getBlockTemps;
    private Object defaultBlockTemp;
    private Method isValid;
    private Method blockTemperature;
    private Method getDummyPlayer;
    private final ThermalCache<ServerLevel, BlockPos, Float> ambientCache = new ThermalCache<>(20, 4096);

    public void clearCache() { ambientCache.clear(); }

    private ColdSweatTemperatureBridge() {}

    @Override
    public String id() {
        return "cold_sweat";
    }

    @Override
    public boolean isActive() {
        initIfNeeded();
        return active;
    }

    @Override
    public float ambientCelsius(ServerLevel level, BlockPos pos) {
        initIfNeeded();
        if (!active) return Float.NaN;
        Float value = ambientCache.get(level, pos.immutable(), level.getGameTime(), () -> sampleAmbient(level, pos));
        return value == null ? Float.NaN : value;
    }

    /** Fresh dummy sampling avoids sharing a room-adjusted cache entry with outdoor climate. */
    public float uncachedAmbient(ServerLevel level, BlockPos pos, boolean outdoor) {
        initIfNeeded();
        if (!active) return Float.NaN;
        LivingEntity dummy = null;
        net.minecraft.world.phys.Vec3 previous = null;
        try {
            dummy = (LivingEntity) getDummyPlayer.invoke(null, level);
            previous = dummy.position();
            if (!outdoor) {
                Float value = sampleAmbient(level, pos);
                return value == null ? Float.NaN : value;
            }
            dummy.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            Class<?> api = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature");
            Class<?> trait = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature$Trait");
            Object world = java.util.Arrays.stream(trait.getEnumConstants())
                    .filter(e -> ((Enum<?>) e).name().equals("WORLD")).findFirst().orElseThrow();
            var modifiers = new java.util.ArrayList<>((java.util.Collection<?>) api
                    .getMethod("getModifiers", LivingEntity.class, trait).invoke(null, dummy, world));
            modifiers.removeIf(m -> switch (m.getClass().getSimpleName()) {
                case "BlockTempModifier", "NearbyEntitiesTempModifier", "EntitiesTempModifier",
                     "WarmthTempModifier", "FrigidnessTempModifier", "HearthTempModifier" -> true;
                default -> false;
            });
            double mc = ((Number) api.getMethod("apply", double.class, LivingEntity.class, trait,
                    java.util.Collection.class, boolean.class).invoke(null, 0d, dummy, world, modifiers, true)).doubleValue();
            return (float) (mc * MC_UNIT_TO_CELSIUS);
        } catch (Exception e) { return Float.NaN; }
        finally { if (dummy != null && previous != null) dummy.setPos(previous); }
    }

    private Float sampleAmbient(ServerLevel level, BlockPos pos) {
        try {
            Object raw = getTemperatureAt.invoke(null, level, pos);
            if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue())) return null;
            double mc = n.doubleValue();
            if (convert != null && unitsMc != null && unitsC != null) {
                Object converted = convert.invoke(null, mc, unitsMc, unitsC, true);
                if (converted instanceof Number c && Float.isFinite(c.floatValue())) return c.floatValue();
            }
            return (float) (mc * MC_UNIT_TO_CELSIUS);
        } catch (Exception e) {
            return null;
        }
    }

    /** Evaluate effects and predicates, never infer their sign from accumulation limits. */
    @Override
    public float blockTemperatureCelsius(Level level, BlockPos pos, BlockState state) {
        initIfNeeded();
        if (!active || getBlockTemps == null) return Float.NaN;
        try {
            Object temps = getBlockTemps.invoke(null, state);
            if (!(temps instanceof java.util.Collection<?> collection) || collection.isEmpty()) return Float.NaN;
            return evaluateBlockEffects(collection, defaultBlockTemp, isValid, blockTemperature,
                    level, pos, state, () -> {
                        LivingEntity dummy = (LivingEntity) getDummyPlayer.invoke(null, level);
                        dummy.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        return dummy;
                    });
        } catch (Exception e) {
            return Float.NaN;
        }
    }

    static float evaluateBlockEffects(java.util.Collection<?> collection, Object fallback,
                                      Method valid, Method temperature, Level level, BlockPos pos,
                                      BlockState state, java.util.concurrent.Callable<LivingEntity> entity) throws Exception {
        double total = 0;
        boolean listed = false;
        LivingEntity dummy = null;
        boolean resolvedDummy = false;
        for (Object effect : collection) {
            if (effect == fallback) continue;
            listed = true;
            if (!Boolean.TRUE.equals(valid.invoke(effect, level, pos, state))) continue;
            if (!resolvedDummy) {
                dummy = entity.call();
                resolvedDummy = true;
            }
            double value = ((Number) temperature.invoke(effect, level, dummy, state, pos, 0d)).doubleValue();
            if (Double.isFinite(value)) total += value;
        }
        return listed ? (float) (total * MC_UNIT_TO_CELSIUS) : Float.NaN;
    }

    private synchronized void initIfNeeded() {
        if (initialized) return;
        initialized = true;
        if (!ModCompat.isLoaded("cold_sweat")) return;
        try {
            Class<?> worldHelper = Class.forName("com.momosoftworks.coldsweat.util.world.WorldHelper");
            getTemperatureAt = worldHelper.getMethod("getTemperatureAt", Level.class, BlockPos.class);
            getDummyPlayer = worldHelper.getMethod("getDummyPlayer", Level.class);
        } catch (Exception e) {
            Townstead.LOGGER.warn("Cold Sweat detected but its world temperature query was not found; using the built-in temperature fallback.");
            return;
        }
        try {
            Class<?> temperature = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature");
            Class<?> units = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature$Units");
            convert = temperature.getMethod("convert", double.class, units, units, boolean.class);
            // Resolved by name: the enum's declaration order is F, C, MC.
            for (Object constant : units.getEnumConstants()) {
                String name = ((Enum<?>) constant).name();
                if (name.equals("MC")) unitsMc = constant;
                if (name.equals("C")) unitsC = constant;
            }
        } catch (Exception ignored) {
            convert = null;
        }
        try {
            Class<?> registry = Class.forName("com.momosoftworks.coldsweat.api.registry.BlockTempRegistry");
            getBlockTemps = registry.getMethod("getBlockTempsFor", BlockState.class);
            defaultBlockTemp = registry.getField("DEFAULT_BLOCK_TEMP").get(null);
            Class<?> blockTemp = Class.forName("com.momosoftworks.coldsweat.api.temperature.block_temp.BlockTemp");
            isValid = blockTemp.getMethod("isValid", Level.class, BlockPos.class, BlockState.class);
            blockTemperature = blockTemp.getMethod("getTemperature", Level.class, LivingEntity.class,
                    BlockState.class, BlockPos.class, double.class);
        } catch (Exception ignored) {
            getBlockTemps = null;
        }
        active = true;
        Townstead.LOGGER.info("Cold Sweat temperature compatibility enabled.");
    }
}
