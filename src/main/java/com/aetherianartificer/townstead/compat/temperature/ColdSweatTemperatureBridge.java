package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.aetherianartificer.townstead.temperature.ThermalProtection;
import net.minecraft.world.entity.player.Player;
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
    /** Marks the modifier as Townstead's, so a replace only ever displaces our own. */
    private static final String INFLUENCE_MARKER = "townstead_influence";
    /** Holder so loading the bridge does not touch the registries before bootstrap. */
    private static final class Tags {
        /** Cold Sweat's hearth, boiler and icebox. */
        static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> THERMAL_SOURCES =
                net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                        net.minecraft.resources.ResourceLocation.tryParse("cold_sweat:thermal_source"));
    }

    private boolean initialized;
    private boolean active;
    private Method getTemperatureAt;
    private Method convert;
    private Object unitsMc;
    private Object unitsC;
    private Method addTemperature;
    private Object traitCore;
    private Object traitBase;
    private Method addModifier;
    private Method getModifierNbt;
    private java.lang.reflect.Constructor<?> simpleModifier;
    private Method expires;
    private Object operationAdd;
    private Object influencePlacement;
    private Method getBlockTemps;
    private Object defaultBlockTemp;
    private Method isValid;
    private Method blockTemperature;
    private Method getDummyPlayer;
    private Class<?> thermalSource;
    private Method lastInput;
    private Method lastOutput;
    private Method applyModifier;
    private Class<?> hearthEntity;
    private Method usingHotFuel;
    private Method usingColdFuel;
    private Method smartEnabled, smokestack, hotFuel, coldFuel, drainHot, drainCold, drainInterval;
    /** Smart hearths Townstead counts as burning while Cold Sweat idles them, by next fuel drain tick. */
    private final java.util.Map<ServerLevel, java.util.Map<Long, Long>> overridden = new java.util.WeakHashMap<>();
    private java.lang.reflect.Constructor<?> insulationSnapshot;
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

    private volatile Boolean seasonModifier;

    /** Cold Sweat's Serene modifier, or the Ecliptic MultiMod Patch's, is registered. */
    @Override
    public boolean modelsSeasons() {
        Boolean known = seasonModifier;
        if (known != null) return known;
        boolean found = false;
        try {
            Method contains = Class.forName("com.momosoftworks.coldsweat.api.registry.TempModifierRegistry")
                    .getMethod("containsKey", net.minecraft.resources.ResourceLocation.class);
            for (String id : new String[]{"sereneseasons:season", "eclipticseasons:season"}) {
                if (Boolean.TRUE.equals(contains.invoke(null, net.minecraft.resources.ResourceLocation.tryParse(id)))) found = true;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
        // The registry fills during load; only a positive answer is final.
        if (found) seasonModifier = true;
        return found;
    }

    /** Cold Sweat's depth regions warm toward the world bottom on their own. */
    @Override
    public boolean modelsGeothermal() {
        return true;
    }

    /** Cold Sweat's insulator registry, so every mod's Cold Sweat garment data reaches villagers. */
    @Override
    public ThermalProtection itemProtection(ItemStack stack) {
        initIfNeeded();
        if (!active) return null;
        return ColdSweatInsulators.protection(stack);
    }

    @Override
    public float ambientCelsius(ServerLevel level, BlockPos pos) {
        initIfNeeded();
        if (!active) return Float.NaN;
        Float value = ambientCache.get(level, pos.immutable(), level.getGameTime(), () -> {
            Float sampled = sampleAmbient(level, pos);
            if (sampled == null || RoomHeatBackend.bypassed() || RoomHeatBackend.room(level, pos).isPresent()) return sampled;
            return sampled + RoomHeatBackend.coldSweatShift(level, pos);
        });
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

    /**
     * Adds {@code shift} to a player's WORLD result as if it entered before the first hearth or
     * Warmth/Frigidness modifier, so those blend from the shifted value. Every modifier from there on
     * is re-run on its stored function, and anything Cold Sweat added after the list is kept.
     */
    public double shiftBeforeThermalSources(java.util.List<?> modifiers, Object trait, double result, double shift) {
        initIfNeeded();
        if (thermalSource == null || lastInput == null || lastOutput == null || applyModifier == null
                || modifiers.isEmpty()) return result + shift;
        try {
            int first = -1;
            for (int i = 0; i < modifiers.size(); i++) {
                if (thermalSource.isInstance(modifiers.get(i))) { first = i; break; }
            }
            if (first < 0) return result + shift;
            double tail = result - ((Number) lastOutput.invoke(modifiers.get(modifiers.size() - 1), trait)).doubleValue();
            double value = ((Number) lastInput.invoke(modifiers.get(first), trait)).doubleValue() + shift;
            for (int i = first; i < modifiers.size(); i++) {
                value = ((Number) applyModifier.invoke(modifiers.get(i), trait, value)).doubleValue();
            }
            return Double.isFinite(value + tail) ? value + tail : result + shift;
        } catch (Exception e) {
            return result + shift;
        }
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
        if (!active) return Float.NaN;
        float running = thermalSource(level, pos, state);
        if (Float.isFinite(running)) return running;
        if (getBlockTemps == null) return Float.NaN;
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

    /** Listed with any real effect; whether it applies here still depends on position. */
    @Override
    public boolean blockMayMatter(BlockState state) {
        initIfNeeded();
        if (!active) return false;
        if (state.is(Tags.THERMAL_SOURCES)) return true;
        if (getBlockTemps == null) return false;
        try {
            if (!(getBlockTemps.invoke(null, state) instanceof java.util.Collection<?> collection)) return false;
            for (Object effect : collection) if (effect != defaultBlockTemp) return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * A hearth, boiler or icebox as a room appliance: 1 while it burns hot fuel, -1 while it uses
     * cold fuel, 0 when idle. NaN for anything else. See {@link #sourceSign}.
     */
    private float thermalSource(Level level, BlockPos pos, BlockState state) {
        if (hearthEntity == null || !state.is(Tags.THERMAL_SOURCES)) return Float.NaN;
        try {
            Object entity = level.getBlockEntity(pos);
            if (!hearthEntity.isInstance(entity)) return Float.NaN;
            float sign = burning(entity);
            if (sign != coldSweatSign(entity) && level instanceof ServerLevel server)
                overridden.computeIfAbsent(server, k -> new java.util.HashMap<>())
                        .putIfAbsent(pos.asLong(), server.getGameTime() + drainInterval(entity));
            return sign;
        } catch (Exception e) {
            return Float.NaN;
        }
    }

    /** What Cold Sweat itself is burning: 1 hot fuel, -1 cold fuel, 0 idle. */
    private float coldSweatSign(Object entity) throws ReflectiveOperationException {
        boolean hot = Boolean.TRUE.equals(usingHotFuel.invoke(entity));
        boolean cold = Boolean.TRUE.equals(usingColdFuel.invoke(entity));
        return (hot ? 1f : 0f) - (cold ? 1f : 0f);
    }

    /** Whether Townstead counts the source as burning; Cold Sweat's own choice unless Townstead owns block heat. */
    private float burning(Object entity) throws ReflectiveOperationException {
        if (smartEnabled == null || !RoomHeatBackend.ownsBlockHeat()) return coldSweatSign(entity);
        return sourceSign(Boolean.TRUE.equals(smartEnabled.invoke(entity)),
                Boolean.TRUE.equals(smokestack.invoke(entity)),
                ((Number) hotFuel.invoke(entity)).intValue(), ((Number) coldFuel.invoke(entity)).intValue(),
                Boolean.TRUE.equals(usingHotFuel.invoke(entity)), Boolean.TRUE.equals(usingColdFuel.invoke(entity)));
    }

    private int drainInterval(Object entity) {
        try {
            return drainInterval == null ? 20 : Math.max(1, ((Number) drainInterval.invoke(entity)).intValue());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 20;
        }
    }

    /**
     * A smart hearth that Townstead counts as burning while Cold Sweat idles it still uses fuel, one
     * unit per Cold Sweat drain interval, until it runs out or Cold Sweat takes over again.
     */
    public void drainOverriddenFuel(net.minecraft.server.MinecraftServer server) {
        if (overridden.isEmpty() || drainHot == null || hearthEntity == null) return;
        for (var levelEntry : overridden.entrySet()) {
            ServerLevel level = levelEntry.getKey();
            long now = level.getGameTime();
            var iterator = levelEntry.getValue().entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                BlockPos pos = BlockPos.of(entry.getKey());
                if (!level.isLoaded(pos)) { iterator.remove(); continue; }
                Object entity = level.getBlockEntity(pos);
                try {
                    if (!hearthEntity.isInstance(entity)) { iterator.remove(); continue; }
                    float sign = burning(entity), own = coldSweatSign(entity);
                    if (sign == own) { iterator.remove(); continue; }
                    if (now < entry.getValue()) continue;
                    if (sign > 0 && own <= 0) drainHot.invoke(entity, 1, true);
                    if (sign < 0 && own >= 0) drainCold.invoke(entity, 1, true);
                    entry.setValue(now + drainInterval(entity));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    iterator.remove();
                }
            }
        }
        overridden.values().removeIf(java.util.Map::isEmpty);
    }

    /**
     * How wet Cold Sweat says the player is: its stored soak over its maximum rain soak, or 1 in
     * water. Its soak keeps updating; only its temperature effect is set aside.
     */
    public float wetness(java.util.List<?> modifiers, Player player) {
        if (player.isInWater()) return 1f;
        double max = configDouble("MAX_RAIN_SOAK");
        for (Object modifier : modifiers) {
            if (!modifier.getClass().getSimpleName().equals("WaterTempModifier")) continue;
            try {
                double soak = ((Number) modifier.getClass().getMethod("getTemperature").invoke(modifier)).doubleValue();
                return (float) Math.max(0, Math.min(1, Math.abs(soak) / (Double.isFinite(max) && max > 0 ? max : 1)));
            } catch (ReflectiveOperationException | RuntimeException e) {
                return 0f;
            }
        }
        return 0f;
    }

    /** What every WORLD modifier of this class added to a player's result, in Cold Sweat units. */
    public double contribution(java.util.List<?> modifiers, Object trait, String simpleName) {
        initIfNeeded();
        if (lastInput == null || lastOutput == null) return 0;
        double total = 0;
        for (Object modifier : modifiers) {
            if (!modifier.getClass().getSimpleName().equals(simpleName)) continue;
            try {
                double in = ((Number) lastInput.invoke(modifier, trait)).doubleValue();
                double out = ((Number) lastOutput.invoke(modifier, trait)).doubleValue();
                if (Double.isFinite(out - in)) total += out - in;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return total;
    }

    /**
     * A fire burns whether anyone is cold or not. In smart mode Cold Sweat only lights the hearth for
     * a player outside the habitable range, so a fueled hearth with a smokestack counts as burning.
     * With both fuels loaded, Cold Sweat's choice decides. Redstone mode keeps the player's switch.
     */
    static float sourceSign(boolean smart, boolean smokestack, int hotFuel, int coldFuel, boolean usingHot, boolean usingCold) {
        boolean hot = usingHot, cold = usingCold;
        if (smart && smokestack && !(hotFuel > 0 && coldFuel > 0)) {
            hot = hotFuel > 0;
            cold = coldFuel > 0;
        }
        return (hot ? 1f : 0f) - (cold ? 1f : 0f);
    }

    /** No hearth insulation at a position: Townstead's room air carries the hearth's heat instead. */
    public Object noInsulation(Level level) {
        initIfNeeded();
        if (insulationSnapshot == null) return null;
        try {
            return insulationSnapshot.newInstance(level.getGameTime(), 0, 0);
        } catch (Exception e) {
            return null;
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

    /**
     * Follows Cold Sweat's own food handling, which picks its trait by whether the effect lasts:
     * an instant change goes on {@code CORE}, which decays back toward base by itself, and a
     * lasting one becomes an expiring modifier on {@code BASE}. {@code Temperature.add} fires the
     * change event and calls {@code updateTemperature}, so the sync and any veto are handled.
     *
     * <p>Cold Sweat expires the modifier itself, so nothing is held in
     * {@link PlayerThermalOffsets} for this backend.</p>
     */
    @Override
    public boolean adjustPlayerBodyCelsius(Player player, float degrees, int durationTicks) {
        if (player == null || player.level().isClientSide || degrees == 0f) return false;
        initIfNeeded();
        if (!active) return false;
        double units = toMcUnits(degrees);
        if (durationTicks <= 0) {
            if (addTemperature == null || traitCore == null) return false;
            try {
                addTemperature.invoke(null, player, traitCore, units);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
        if (addModifier == null || simpleModifier == null || operationAdd == null
                || traitBase == null || influencePlacement == null) {
            return false;
        }
        try {
            Object modifier = simpleModifier.newInstance(units, operationAdd);
            ((net.minecraft.nbt.CompoundTag) getModifierNbt.invoke(modifier))
                    .putBoolean(INFLUENCE_MARKER, true);
            expires.invoke(modifier, durationTicks);
            addModifier.invoke(null, player, modifier, traitBase, influencePlacement);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Cold Sweat stores traits in its own unit; a delta converts unscaled by the zero point. */
    private double toMcUnits(float celsius) {
        if (convert != null && unitsC != null && unitsMc != null) {
            try {
                Object value = convert.invoke(null, (double) celsius, unitsC, unitsMc, false);
                if (value instanceof Number n) return n.doubleValue();
            } catch (Exception ignored) {}
        }
        return celsius / MC_UNIT_TO_CELSIUS;
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
            Class<?> temperature = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature");
            Class<?> trait = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature$Trait");
            addTemperature = temperature.getMethod("add", LivingEntity.class, trait, double.class);
            // CORE is the body heat a meal or a fire changes; BODY is derived and not writable.
            // BASE is what a lasting influence shifts, the trait CORE converges toward.
            for (Object constant : trait.getEnumConstants()) {
                String name = ((Enum<?>) constant).name();
                if (name.equals("CORE")) traitCore = constant;
                if (name.equals("BASE")) traitBase = constant;
            }
            Class<?> tempModifier = Class.forName(
                    "com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier");
            Class<?> placement = Class.forName("com.momosoftworks.coldsweat.api.util.placement.Placement");
            Class<?> mode = Class.forName("com.momosoftworks.coldsweat.api.util.placement.Mode");
            Class<?> order = Class.forName("com.momosoftworks.coldsweat.api.util.placement.Order");
            addModifier = temperature.getMethod("addModifier",
                    LivingEntity.class, tempModifier, trait, placement);
            getModifierNbt = tempModifier.getMethod("getNBT");
            expires = tempModifier.getMethod("expires", int.class);

            Class<?> simple = Class.forName(
                    "com.momosoftworks.coldsweat.api.temperature.modifier.SimpleTempModifier");
            Class<?> operation = Class.forName(
                    "com.momosoftworks.coldsweat.api.temperature.modifier.SimpleTempModifier$Operation");
            simpleModifier = simple.getConstructor(double.class, operation);
            for (Object constant : operation.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals("ADD")) operationAdd = constant;
            }

            // Replace only a modifier Townstead placed. Matching on the class would also catch a
            // datapack's cold_sweat:simple modifier, which is the same class and not ours to move;
            // Cold Sweat never constructs one itself, it only registers the type for data to use.
            Object modeReplace = null, orderFirst = null;
            for (Object constant : mode.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals("REPLACE")) modeReplace = constant;
            }
            for (Object constant : order.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals("FIRST")) orderFirst = constant;
            }
            java.util.function.Predicate<Object> ours = candidate -> {
                try {
                    return ((net.minecraft.nbt.CompoundTag) getModifierNbt.invoke(candidate))
                            .getBoolean(INFLUENCE_MARKER);
                } catch (Exception ignored) {
                    return false;
                }
            };
            Object replaceOurs = placement.getMethod("of", mode, order, java.util.function.Predicate.class)
                    .invoke(null, modeReplace, orderFirst, ours);
            influencePlacement = placement.getMethod("orElse", placement)
                    .invoke(replaceOurs, placement.getField("LAST").get(null));
        } catch (Exception ignored) {
            addTemperature = null;
            traitCore = null;
        }
        try {
            Class<?> trait = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature$Trait");
            Class<?> tempModifier = Class.forName(
                    "com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier");
            lastInput = tempModifier.getMethod("getLastInput", trait);
            lastOutput = tempModifier.getMethod("getLastOutput", trait);
            applyModifier = tempModifier.getMethod("apply", trait, double.class);
            thermalSource = Class.forName(
                    "com.momosoftworks.coldsweat.api.temperature.modifier.ThermalSourceTempModifier");
        } catch (Exception ignored) {
            thermalSource = null;
        }
        try {
            hearthEntity = Class.forName("com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity");
            usingHotFuel = hearthEntity.getMethod("isUsingHotFuel");
            usingColdFuel = hearthEntity.getMethod("isUsingColdFuel");
        } catch (Exception ignored) {
            hearthEntity = null;
        }
        if (hearthEntity != null) {
            try {
                smartEnabled = hearthEntity.getMethod("isSmartEnabled");
                smokestack = hearthEntity.getMethod("hasSmokestack");
                hotFuel = hearthEntity.getMethod("getHotFuel");
                coldFuel = hearthEntity.getMethod("getColdFuel");
            } catch (Exception ignored) {
                smartEnabled = null;
            }
            try {
                drainHot = hearthEntity.getMethod("drainHotFuel", int.class, boolean.class);
                drainCold = hearthEntity.getMethod("drainColdFuel", int.class, boolean.class);
                drainInterval = hearthEntity.getMethod("getFuelDrainInterval");
            } catch (Exception ignored) {
                drainHot = null;
            }
        }
        try {
            insulationSnapshot = Class.forName("com.momosoftworks.coldsweat.util.world.WorldHelper$InsulationSnapshot")
                    .getConstructor(long.class, int.class, int.class);
        } catch (Exception ignored) {
            insulationSnapshot = null;
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

    /** Its habitable range, from its live config. */
    @Override
    public float[] comfortRangeCelsius() {
        double low = configDouble("MIN_TEMP"), high = configDouble("MAX_TEMP");
        return Double.isFinite(low) && Double.isFinite(high) ? new float[]{(float) (low * 25), (float) (high * 25)} : null;
    }

    /** Its acclimation ranges: the cold and hot shifts of the minimum and maximum habitable temperature. */
    @Override
    public com.aetherianartificer.townstead.temperature.ThermalAcclimation.Limits acclimationLimits() {
        double[] min = configPair("MIN_ACCLIMATION_RANGE"), max = configPair("MAX_ACCLIMATION_RANGE");
        if (min == null || max == null) return null;
        return new com.aetherianartificer.townstead.temperature.ThermalAcclimation.Limits(
                (float) (min[0] * 25), (float) (max[0] * 25), (float) (min[1] * 25), (float) (max[1] * 25), Float.NaN);
    }

    /** Its acclimation speed is in its units per minute; the share of the widest shift that covers. */
    @Override
    public float acclimationRatePerMinute() {
        double speed = configDouble("ACCLIMATION_SPEED");
        var limits = acclimationLimits();
        if (!Double.isFinite(speed) || limits == null) return Float.NaN;
        float widest = Math.max(Math.max(Math.abs(limits.coldMin()), Math.abs(limits.coldMax())),
                Math.max(Math.abs(limits.hotMin()), Math.abs(limits.hotMax())));
        return widest <= 0 ? Float.NaN : (float) (speed * 25 / widest);
    }

    private Object configValue(String field) {
        if (!isActive()) return null;
        try {
            Object holder = Class.forName("com.momosoftworks.coldsweat.config.ConfigSettings").getField(field).get(null);
            return holder == null ? null : holder.getClass().getMethod("get").invoke(holder);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    private double configDouble(String field) {
        return configValue(field) instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    private double[] configPair(String field) {
        Object pair = configValue(field);
        if (pair == null) return null;
        try {
            Object a = pair.getClass().getMethod("getFirst").invoke(pair), b = pair.getClass().getMethod("getSecond").invoke(pair);
            return a instanceof Number x && b instanceof Number y ? new double[]{x.doubleValue(), y.doubleValue()} : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public net.minecraft.resources.ResourceLocation playerImmunityEffect() {
        return net.minecraft.resources.ResourceLocation.tryParse("cold_sweat:grace");
    }
}
