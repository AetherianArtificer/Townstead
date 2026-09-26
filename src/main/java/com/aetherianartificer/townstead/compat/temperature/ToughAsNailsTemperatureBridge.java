package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.temperature.ThermalBlocks;
import com.aetherianartificer.townstead.temperature.PlayerThermalOffsets;
import com.aetherianartificer.townstead.temperature.TemperatureSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Tough As Nails has five temperature levels and no number, so each level maps to a representative
 * Celsius value. Pure reflection against {@code toughasnails.api.temperature.TemperatureHelper}.
 * On activation it also registers a proximity modifier with the mod so every block Townstead
 * tags as a heat or cooling source warms or cools the player the way it does villagers.
 */
public final class ToughAsNailsTemperatureBridge implements AmbientTemperatureBridge {
    public static final ToughAsNailsTemperatureBridge INSTANCE = new ToughAsNailsTemperatureBridge();

    private static final float TAG_PIECE = 0.5f;
    // A nudge with no stated duration still needs to outlast TAN's step delay; ten seconds
    // reads as "a moment ago I ate something hot".
    private static final int DEFAULT_INFLUENCE_TICKS = 200;
    // toughasnails player_temperature_change_delay default.
    private static final int DEFAULT_CHANGE_DELAY_TICKS = 125;
    private static final TagKey<Block> HEATING_BLOCKS = tag(Registries.BLOCK, "heating_blocks");
    private static final TagKey<Block> COOLING_BLOCKS = tag(Registries.BLOCK, "cooling_blocks");
    private static final TagKey<Item> HEATING_ARMOR = tag(Registries.ITEM, "heating_armor");
    private static final TagKey<Item> COOLING_ARMOR = tag(Registries.ITEM, "cooling_armor");

    private boolean initialized;
    private boolean active;
    private boolean playerModifierRegistered;
    private boolean changeDelayResolved;
    private int changeDelay;
    private Method getTemperatureAtPos;
    private Method isHeatingBlock;
    private Method isCoolingBlock;
    private Method regulatorEffect;

    private ToughAsNailsTemperatureBridge() {}

    /** Read TAN's flood-filled coverage from loaded block entities; never load chunks for a sample. */
    public float regulatedCelsius(ServerLevel level, BlockPos pos, float ambient) {
        if (!isActive() || regulatorEffect == null) return ambient;
        int heating = 0, cooling = 0, neutral = 0;
        for (int x = (pos.getX() - 20) >> 4; x <= (pos.getX() + 20) >> 4; x++) {
            for (int z = (pos.getZ() - 20) >> 4; z <= (pos.getZ() + 20) >> 4; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    if (!regulatorEffect.getDeclaringClass().isInstance(blockEntity)) continue;
                    BlockState state = blockEntity.getBlockState();
                    if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED)
                            && !state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED)) continue;
                    try {
                        switch (((Enum<?>) regulatorEffect.invoke(blockEntity, pos)).name()) {
                            case "HEATING" -> heating++;
                            case "COOLING" -> cooling++;
                            case "NEUTRALIZING" -> neutral++;
                        }
                    } catch (ReflectiveOperationException e) {
                        Townstead.LOGGER.debug("Unable to query TAN regulator at {}", blockEntity.getBlockPos(), e);
                    }
                }
            }
        }
        return TanTemperaturePolicy.regulate(ambient, heating, cooling, neutral);
    }

    @Override
    public String id() {
        return "tough_as_nails";
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
        try {
            Object levelEnum = getTemperatureAtPos.invoke(null, level, pos);
            if (!(levelEnum instanceof Enum<?> e)) return Float.NaN;
            return switch (e.name()) {
                case "ICY" -> -10f;
                case "COLD" -> 5f;
                case "NEUTRAL" -> 20f;
                case "WARM" -> 30f;
                case "HOT" -> 40f;
                default -> Float.NaN;
            };
        } catch (Exception ex) {
            return Float.NaN;
        }
    }

    public float outdoorCelsius(ServerLevel level, BlockPos pos) {
        try {
            Object result = Class.forName("toughasnails.temperature.TemperatureHelperImpl")
                    .getMethod("getTemperatureAtPosWithoutProximity", Level.class, BlockPos.class).invoke(null, level, pos);
            return switch (((Enum<?>) result).name()) {
                case "ICY" -> -10; case "COLD" -> 5; case "NEUTRAL" -> 20;
                case "WARM" -> 30; case "HOT" -> 40; default -> Float.NaN;
            };
        } catch (Exception e) { return Float.NaN; }
    }

    private static <T> TagKey<T> tag(ResourceKey<? extends Registry<T>> registry, String path) {
        //? if >=1.21 {
        return TagKey.create(registry, ResourceLocation.fromNamespaceAndPath("toughasnails", path));
        //?} else {
        /*return TagKey.create(registry, new ResourceLocation("toughasnails", path));
        *///?}
    }

    /** The mod's own block tags, so anything a pack adds to them reaches villagers untouched. */
    @Override
    public float blockTemperatureCelsius(Level level, BlockPos pos, BlockState state) {
        if (!isActive()) return Float.NaN;
        if (!state.is(HEATING_BLOCKS) && !state.is(COOLING_BLOCKS)) return Float.NaN;
        try {
            if (Boolean.TRUE.equals(isCoolingBlock.invoke(null, state))) return -8f;
            if (Boolean.TRUE.equals(isHeatingBlock.invoke(null, state))) return 10f;
            return 0f;
        } catch (Exception ignored) {
            return 0f;
        }
    }

    @Override
    public boolean blockMayMatter(BlockState state) {
        return isActive() && (state.is(HEATING_BLOCKS) || state.is(COOLING_BLOCKS));
    }

    @Override
    public float itemInsulationCelsius(ItemStack stack) {
        if (!isActive() || stack == null || stack.isEmpty()) return Float.NaN;
        if (stack.is(HEATING_ARMOR)) return TAG_PIECE;
        if (stack.is(COOLING_ARMOR)) return -TAG_PIECE;
        return Float.NaN;
    }

    private synchronized void initIfNeeded() {
        if (initialized) return;
        initialized = true;
        if (!ModCompat.isLoaded("toughasnails")) return;
        Class<?> helper;
        try {
            helper = Class.forName("toughasnails.api.temperature.TemperatureHelper");
            getTemperatureAtPos = helper.getMethod("getTemperatureAtPos", Level.class, BlockPos.class);
            isHeatingBlock = helper.getMethod("isHeatingBlock", BlockState.class);
            isCoolingBlock = helper.getMethod("isCoolingBlock", BlockState.class);
            active = true;
            Townstead.LOGGER.info("Tough As Nails temperature compatibility enabled.");
        } catch (Exception e) {
            Townstead.LOGGER.warn("Tough As Nails detected but its positional temperature query was not found; using the built-in temperature fallback.");
            return;
        }
        registerProximityModifier(helper);
        registerPlayerModifier(helper);
        try {
            regulatorEffect = Class.forName("toughasnails.block.entity.ThermoregulatorBlockEntity")
                    .getMethod("getEffectAtPos", BlockPos.class);
        } catch (ReflectiveOperationException e) {
            Townstead.LOGGER.warn("Tough As Nails regulator coverage API unavailable", e);
        }
    }

    /**
     * TAN derives the player's level every evaluation rather than storing one, so a warming is
     * expressed by answering its question differently while the influence lasts. The registered
     * modifier is the only place an outside caller gets a say; writing the level directly would
     * be recomputed away on the next tick.
     */
    private void registerPlayerModifier(Class<?> helper) {
        try {
            Class<?> modifier = Class.forName("toughasnails.api.temperature.IPlayerTemperatureModifier");
            Class<?> level = Class.forName("toughasnails.api.temperature.TemperatureLevel");
            Method increment = level.getMethod("increment", int.class);
            Object proxy = Proxy.newProxyInstance(modifier.getClassLoader(), new Class<?>[] {modifier}, (self, method, args) -> {
                if ("modify".equals(method.getName()) && args != null && args.length == 2
                        && args[0] instanceof Player player) {
                    float offset = PlayerThermalOffsets.get(player);
                    if (offset == 0f) return args[1];
                    int steps = Math.round(offset / TemperatureSettings.get().celsiusPerTanStep());
                    // Anything the author meant as a nudge moves at least one step, or a
                    // warming smaller than the step width would read as doing nothing.
                    if (steps == 0) steps = offset > 0 ? 1 : -1;
                    // increment clamps to the ICY..HOT range itself.
                    return increment.invoke(args[1], steps);
                }
                return switch (method.getName()) {
                    case "toString" -> "TownsteadThermalInfluence";
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    default -> null;
                };
            });
            helper.getMethod("registerPlayerTemperatureModifier", modifier).invoke(null, proxy);
            playerModifierRegistered = true;
            Townstead.LOGGER.info("Registered Townstead thermal influences with Tough As Nails.");
        } catch (Exception e) {
            Townstead.LOGGER.debug("Tough As Nails player modifier hook not available: {}", e.toString());
        }
    }

    /**
     * The influence lives in {@link PlayerThermalOffsets}; the registered modifier reads it.
     *
     * <p>TAN shifts the player one step at a time and then waits out
     * {@code player_temperature_change_delay} before the next, so an influence shorter than that
     * delay expires before the level has moved at all and reads as doing nothing. The duration is
     * therefore held to at least that long: the alternative is an author writing a brief warming
     * and seeing no effect whatsoever.</p>
     */
    @Override
    public boolean adjustPlayerBodyCelsius(Player player, float degrees, int durationTicks) {
        if (player == null || player.level().isClientSide || degrees == 0f) return false;
        initIfNeeded();
        if (!active || !playerModifierRegistered) return false;
        int requested = durationTicks > 0 ? durationTicks : DEFAULT_INFLUENCE_TICKS;
        PlayerThermalOffsets.set(player, degrees, Math.max(requested, changeDelayTicks()));
        return true;
    }

    /** TAN's configured step delay, or its own default when the config cannot be read. */
    private int changeDelayTicks() {
        if (!changeDelayResolved) {
            changeDelayResolved = true;
            try {
                Object config = Class.forName("toughasnails.init.ModConfig")
                        .getField("temperature").get(null);
                changeDelay = config.getClass().getField("playerTemperatureChangeDelay").getInt(config);
            } catch (Exception ignored) {
                changeDelay = DEFAULT_CHANGE_DELAY_TICKS;
            }
            if (changeDelay <= 0) changeDelay = DEFAULT_CHANGE_DELAY_TICKS;
        }
        return changeDelay;
    }

    /** Townstead's heat and cooling sources become Tough As Nails proximity blocks, so the player feels the villagers' hearth. */
    private static void registerProximityModifier(Class<?> helper) {
        try {
            Class<?> modifier = Class.forName("toughasnails.api.temperature.IProximityBlockModifier");
            Class<?> type = Class.forName("toughasnails.api.temperature.IProximityBlockModifier$Type");
            Object heating = null, cooling = null, none = null;
            for (Object constant : type.getEnumConstants()) {
                switch (((Enum<?>) constant).name()) {
                    case "HEATING" -> heating = constant;
                    case "COOLING" -> cooling = constant;
                    case "NONE" -> none = constant;
                }
            }
            if (heating == null || cooling == null || none == null) return;
            final Object heat = heating, cool = cooling, neutral = none;
            Object proxy = Proxy.newProxyInstance(modifier.getClassLoader(), new Class<?>[] {modifier}, (self, method, args) -> {
                if ("getProximityType".equals(method.getName()) && args != null && args.length == 3
                        && args[2] instanceof BlockState state) {
                    // TAN already evaluated its own tags and activation predicates. Do not
                    // reintroduce inactive TAN blocks or recursively consult mod bridges.
                    if (state.is(HEATING_BLOCKS) || state.is(COOLING_BLOCKS)) return neutral;
                    int source = ThermalBlocks.taggedSource(state);
                    if (source > 0) return heat;
                    if (source < 0) return cool;
                    return neutral;
                }
                return switch (method.getName()) {
                    case "toString" -> "TownsteadThermalProximity";
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    default -> null;
                };
            });
            helper.getMethod("registerProximityBlockModifier", modifier).invoke(null, proxy);
            Townstead.LOGGER.info("Registered Townstead heat and cooling sources with Tough As Nails.");
        } catch (Exception e) {
            Townstead.LOGGER.debug("Tough As Nails proximity modifier hook not available: {}", e.toString());
        }
    }
}
