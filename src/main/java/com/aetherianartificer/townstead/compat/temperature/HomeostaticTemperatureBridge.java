package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.thirst.HomeostaticEntityCompat;
import com.aetherianartificer.townstead.temperature.ThermalCache;
import com.aetherianartificer.townstead.temperature.ThermalProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Homeostatic's temperature, in Celsius. The mod works in Minecraft biome units ({@code
 * TempHelper.convertMcTemp}: 1.634 is a 37 C core) and measures only around a player: its
 * environment calculation takes a {@code ServerPlayer} and writes that player's wetness. So the
 * villager-side ambient re-runs its own climate steps at a position: height- and season-adjusted
 * biome temperature over the same 7 by 7 chunk sample, humidity, the day and night swing, then the
 * heat index. Its radiation term is left out, since heat sources reach Townstead as block opinions.
 * Under {@code climateAuthority: townstead} the player side runs the other way: Townstead's climate
 * is written into the mod ({@code HomeostaticRoomQueryMixin}).
 */
public final class HomeostaticTemperatureBridge implements AmbientTemperatureBridge {
    public static final HomeostaticTemperatureBridge INSTANCE = new HomeostaticTemperatureBridge();
    private static final String MOD_ID = HomeostaticEntityCompat.MOD_ID;
    /** The mod's black-globe term per radiation point, and the share of it that reaches the air. */
    public static final float RADIATION_CELSIUS = 0.01498f * 0.25f;
    /** A lined garment, worth two of Townstead's tagged warm pieces. */
    private static final float INSULATED_PIECE = 1.0f;
    private static final int CHUNK_RANGE = 3;
    private static final ThermalCache<ServerLevel, Long, Float> AMBIENT = new ThermalCache<>(100, 4096);

    private boolean initialized;
    private boolean climate;
    private Method heightAdjusted, seasonAdjusted, humidity, dayNight, heatIndex, radiation, maxRadiation;

    private HomeostaticTemperatureBridge() {}

    private static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        //?} else {
        /*return new ResourceLocation(MOD_ID, path);
        *///?}
    }

    private static final TagKey<Item> INSULATED_ARMOR = TagKey.create(Registries.ITEM, id("insulated_armor"));

    @Override
    public String id() {
        return "homeostatic";
    }

    @Override
    public boolean isActive() {
        return ModCompat.isLoaded(MOD_ID);
    }

    private synchronized void init() {
        if (initialized) return;
        initialized = true;
        if (!isActive()) return;
        try {
            Class<?> data = Class.forName("homeostatic.common.temperature.EnvironmentData");
            heightAdjusted = declared(data, "getHeightAdjustedTemperature", ServerLevel.class, Holder.class, BlockPos.class);
            seasonAdjusted = declared(data, "getSeasonAdjustedTemperature", ServerLevel.class, Holder.class, float.class, BlockPos.class);
            humidity = declared(data, "getBiomeHumidity", ServerLevel.class, Holder.class, BlockPos.class);
            dayNight = declared(data, "getDayNightOffset", ServerLevel.class, Holder.class, double.class);
            heatIndex = Class.forName("homeostatic.util.TempHelper").getMethod("getHeatIndex", float.class, double.class);
            climate = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            Townstead.LOGGER.warn("Homeostatic's climate steps were not found; villager ambient uses Townstead's climate.", e);
        }
        try {
            radiation = Class.forName("homeostatic.common.block.BlockRadiationManager").getMethod("getBlockRadiation",
                    net.minecraft.world.level.block.Block.class);
            maxRadiation = Class.forName("homeostatic.common.block.BlockRadiation").getMethod("maxRadiation", BlockState.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {}
    }

    private static Method declared(Class<?> owner, String name, Class<?>... types) throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    /** Homeostatic units to Celsius, the mod's own {@code convertMcTemp(t, false)}. */
    public static float celsius(double mc) {
        return (float) ((25.27027027 + 44.86486486 * mc - 32) * 0.5556);
    }

    /** Celsius to Homeostatic units, the mod's own {@code convertTemp(t, false)}. */
    public static float units(double celsius) {
        return (float) ((celsius / 0.5556 + 32 - 25.27027027) / 44.86486486);
    }

    @Override
    public boolean modelsSeasons() {
        return ModCompat.isLoaded("sereneseasons") || ModCompat.isLoaded("eclipticseasons")
                || ModCompat.isLoaded("homeostaticseasons");
    }

    @Override
    public float ambientCelsius(ServerLevel level, BlockPos pos) {
        init();
        if (!climate) return Float.NaN;
        long key = BlockPos.asLong(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
        Float value = AMBIENT.get(level, key, level.getGameTime(), () -> sample(level, pos.immutable()));
        return value == null ? Float.NaN : value;
    }

    private Float sample(ServerLevel level, BlockPos pos) {
        try {
            boolean overworld = level.dimension() == Level.OVERWORLD;
            boolean underground = overworld && !level.canSeeSky(pos.above()) && pos.getY() < level.getSeaLevel();
            boolean weather = level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
            float dry = 0f;
            double wet = 0;
            int samples = 0;
            for (int x = -CHUNK_RANGE; x <= CHUNK_RANGE; x++) {
                for (int z = -CHUNK_RANGE; z <= CHUNK_RANGE; z++) {
                    BlockPos at = pos.offset(x * 16, 0, z * 16);
                    if (!level.isLoaded(at)) continue;
                    Holder<Biome> biome = level.getBiome(at);
                    float temperature = (float) heightAdjusted.invoke(null, level, biome, at);
                    dry += underground ? temperature : (float) seasonAdjusted.invoke(null, level, biome, temperature, at);
                    if (weather) wet += (double) humidity.invoke(null, level, biome, at);
                    samples++;
                }
            }
            if (samples == 0) return null;
            double relative = wet / samples;
            float air = dry / samples;
            if (!underground) air += (float) dayNight.invoke(null, level, level.getBiome(pos), relative);
            float felt = ((Number) heatIndex.invoke(null, air, relative)).floatValue();
            // Its sheltered blend, with the black globe at the dry temperature when no source is near.
            return celsius(felt * 0.7f + air * 0.3f);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Radiation at contact, on the same air share as its own calculation; zero while unlit. */
    @Override
    public float blockTemperatureCelsius(Level level, BlockPos pos, BlockState state) {
        init();
        if (radiation == null || maxRadiation == null) return Float.NaN;
        try {
            Object entry = radiation.invoke(null, state.getBlock());
            if (entry == null) return Float.NaN;
            if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) return 0f;
            return (float) ((double) maxRadiation.invoke(entry, state)) * RADIATION_CELSIUS;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Float.NaN;
        }
    }

    @Override
    public boolean blockMayMatter(BlockState state) {
        init();
        if (radiation == null) return false;
        try {
            return radiation.invoke(null, state.getBlock()) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Its insulation lining, by the flag its sewing recipe writes or its insulated armor tag. */
    @Override
    public ThermalProtection itemProtection(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isActive()) return null;
        return lined(stack, "insulation") || stack.is(INSULATED_ARMOR)
                ? new ThermalProtection(INSULATED_PIECE, 0, 0, 0) : null;
    }

    private static boolean lined(ItemStack stack, String flag) {
        //? if >=1.21 {
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id("armor"));
        return type != null && stack.get(type) instanceof net.minecraft.world.item.component.CustomData data
                && data.copyTag().contains(flag);
        //?} else {
        /*return stack.getTag() != null && stack.getTag().contains(flag);
        *///?}
    }

    /** Between its parity bounds, about 21 to 27 C. */
    @Override
    public float[] comfortRangeCelsius() {
        return isActive() ? new float[]{celsius(0.997), celsius(1.220)} : null;
    }

    private java.lang.invoke.MethodHandle wetnessCapability;
    private Method wetnessLevel, setWetness;
    private Object platform;

    private Object wetness(Player player) {
        try {
            if (wetnessCapability == null) {
                platform = Class.forName("homeostatic.platform.Services").getField("PLATFORM").get(null);
                wetnessCapability = java.lang.invoke.MethodHandles.publicLookup().findVirtual(
                        Class.forName("homeostatic.platform.services.IPlatform"), "getWetnessCapability",
                        java.lang.invoke.MethodType.methodType(Optional.class, Player.class));
            }
            return wetnessCapability.invoke(platform, player) instanceof Optional<?> data ? data.orElse(null) : null;
        } catch (Throwable e) {
            return null;
        }
    }

    /** Its wetness level runs 0 to 20. */
    @Override
    public float playerWetness(Player player) {
        if (!isActive()) return Float.NaN;
        Object data = wetness(player);
        if (data == null) return player.isInWaterOrRain() ? 1f : 0f;
        try {
            if (wetnessLevel == null) wetnessLevel = data.getClass().getMethod("getWetnessLevel");
            return Math.max(0f, Math.min(1f, ((Number) wetnessLevel.invoke(data)).intValue() / 20f));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Float.NaN;
        }
    }

    /** It has no switch for wetness, so a dry Root's level is held at zero. */
    @Override
    public void keepPlayerDry(Player player, boolean dry) {
        if (!dry || player.level().isClientSide || !isActive()) return;
        Object data = wetness(player);
        if (data == null) return;
        try {
            if (setWetness == null) setWetness = data.getClass().getMethod("setWetnessLevel", int.class);
            setWetness.invoke(data, 0);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }
}
