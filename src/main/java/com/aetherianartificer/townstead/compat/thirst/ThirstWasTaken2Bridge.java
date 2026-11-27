package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.mca.McaSicknessAdapter;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * ThirstWasTaken2 ({@code thirstwastaken2}), a separate mod from Thirst Was Taken. Everything it can
 * answer goes through its public {@code com.thirstwastaken2.api.ThirstApi}; the climate curve, the
 * per-grade quenched cut and the sickness table are read from its internals with a fallback each.
 *
 * <p>Water grades: 0 Dirty, 1 Murky, 2 Clean, 3 Pure. Heat stops at Clean, so Clean is finished
 * water for the purification line. Sea water has no grade, never hydrates, and is never offered.</p>
 */
public final class ThirstWasTaken2Bridge implements ThirstCompatBridge {
    public static final ThirstWasTaken2Bridge INSTANCE = new ThirstWasTaken2Bridge();
    public static final String MOD_ID = "thirstwastaken2";
    static final int CLEAN = 2;
    private static final int[] FALLBACK_QUENCHED_PERCENT = {0, 50, 100, 100};
    private static final float NETHER_MODIFIER = 3.0f;

    //? if >=1.21 {
    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/thirst_icons.png");
    //?} else {
    /*private static final ResourceLocation ICONS = new ResourceLocation(MOD_ID, "textures/gui/thirst_icons.png");
    *///?}

    private boolean initialized;
    private boolean active;
    private Method thirstValues, isWaterContainer, isSalt, purity, waterBottle, playerThirst, playerDrink, playerEnabled;
    private Method climateFactor, configGet, skinIs, skinRemove, sicknessKey, sicknessHolder;
    private Method sampleAt, isStamped, setQuality;
    private Field quenchedPercent, depletion, sicknessEffects, lineChance, lineSeconds, lineLevel, lineGroup;
    private String[] grades;

    private ThirstWasTaken2Bridge() {}

    @Override
    public boolean isActive() {
        init();
        return active;
    }

    private synchronized void init() {
        if (initialized) return;
        initialized = true;
        if (!ModCompat.isLoaded(MOD_ID)) return;
        try {
            Class<?> api = Class.forName("com.thirstwastaken2.api.ThirstApi");
            int version = api.getField("API_VERSION").getInt(null);
            if (version < 2) {
                Townstead.LOGGER.warn("ThirstWasTaken2 API version {} is older than 2; villager thirst integration disabled.", version);
                return;
            }
            thirstValues = api.getMethod("thirstValues", ItemStack.class);
            isWaterContainer = api.getMethod("isWaterContainer", ItemStack.class);
            isSalt = api.getMethod("isSalt", ItemStack.class);
            purity = api.getMethod("purity", ItemStack.class);
            waterBottle = api.getMethod("waterBottle", int.class);
            playerThirst = api.getMethod("thirst", Player.class);
            playerDrink = api.getMethod("drink", Player.class, int.class, int.class);
            playerEnabled = api.getMethod("isEnabled", Player.class);
            active = true;
            Townstead.LOGGER.info("ThirstWasTaken2 compatibility enabled.");
        } catch (ReflectiveOperationException | LinkageError e) {
            Townstead.LOGGER.warn("Failed to initialize ThirstWasTaken2 compatibility. Villager thirst integration disabled.", e);
            return;
        }
        // Internal reads: each falls back to the mod's shipped default when it moves.
        try {
            climateFactor = Class.forName("com.thirstwastaken2.data.ThirstManager")
                    .getMethod("climateFactor", float.class, boolean.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        try {
            Class<?> config = Class.forName("com.thirstwastaken2.config.ThirstConfig");
            configGet = config.getMethod("get");
            quenchedPercent = config.getField("quenchedPercent");
            depletion = config.getField("thirstDepletionModifier");
            sicknessEffects = config.getField("sicknessEffects");
            Class<?> line = Class.forName("com.thirstwastaken2.config.SicknessEffect");
            sicknessKey = line.getMethod("key", net.minecraft.world.Difficulty.class);
            sicknessHolder = line.getMethod("holder");
            lineChance = line.getField("chance");
            lineSeconds = line.getField("seconds");
            lineLevel = line.getField("level");
            lineGroup = line.getField("group");
            grades = (String[]) line.getField("GRADES").get(null);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            sicknessEffects = null;
        }
        try {
            Class<?> purity = Class.forName("com.thirstwastaken2.purity.WaterPurity");
            sampleAt = purity.getMethod("sampleAt", Level.class, BlockPos.class);
            isStamped = purity.getMethod("isStamped", ItemStack.class);
            setQuality = purity.getMethod("setQuality", ItemStack.class, Class.forName("com.thirstwastaken2.purity.WaterQuality"));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            sampleAt = null;
        }
        try {
            Class<?> skin = Class.forName("com.thirstwastaken2.item.WaterskinItem");
            skinIs = skin.getMethod("is", ItemStack.class);
            skinRemove = skin.getMethod("removeWater", ItemStack.class, int.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {}
    }

    private Object call(Method method, Object... args) {
        if (method == null) return null;
        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private boolean flag(Method method, ItemStack stack) {
        return !stack.isEmpty() && Boolean.TRUE.equals(call(method, stack));
    }

    private int[] values(ItemStack stack) {
        if (stack.isEmpty() || !isActive()) return null;
        return call(thirstValues, stack) instanceof int[] value && value.length >= 2 ? value : null;
    }

    private Object config() {
        return call(configGet);
    }

    public boolean salty(ItemStack stack) {
        return isActive() && flag(isSalt, stack);
    }

    @Override
    public boolean itemRestoresThirst(ItemStack stack) {
        int[] value = values(stack);
        return value != null && value[0] > 0 && !salty(stack);
    }

    /** Water of any grade, and whatever the item's own use animation calls a drink. */
    @Override
    public boolean isDrink(ItemStack stack) {
        if (!itemRestoresThirst(stack)) return false;
        return flag(isWaterContainer, stack) || stack.getUseAnimation() == UseAnim.DRINK;
    }

    @Override
    public boolean isPurityWaterContainer(ItemStack stack) {
        return isActive() && flag(isWaterContainer, stack) && !salty(stack);
    }

    @Override
    public int hydration(ItemStack stack) {
        int[] value = values(stack);
        return value == null ? 0 : value[0];
    }

    /** The mod cuts quenched by grade when the drink is water; the same cut applies here. */
    @Override
    public int quenched(ItemStack stack) {
        int[] value = values(stack);
        if (value == null) return 0;
        if (!isPurityWaterContainer(stack)) return value[1];
        int grade = purity(stack);
        if (grade < 0) return value[1];
        int[] percent = FALLBACK_QUENCHED_PERCENT;
        Object config = config();
        try {
            if (config != null && quenchedPercent != null && quenchedPercent.get(config) instanceof int[] read
                    && read.length > grade) percent = read;
        } catch (IllegalAccessException ignored) {}
        return value[1] * percent[Math.min(grade, percent.length - 1)] / 100;
    }

    @Override
    public int purity(ItemStack stack) {
        if (stack.isEmpty() || !isActive()) return -1;
        return call(purity, stack) instanceof Integer grade ? grade : -1;
    }

    @Override
    public int finishedPurity() {
        return CLEAN;
    }

    /** Sickness is the mod's own table; see {@link #applyWaterSickness}. */
    @Override
    public PurityResult evaluatePurity(int purity, RandomSource random) {
        return new PurityResult(true, false, false, purity);
    }

    /**
     * The config's sickness lines for the world's difficulty and the water's grade, each rolled once
     * or once per group, as the mod rolls them for a player. Effects that only tick on players are
     * carried by {@code ThirstWasTaken2EntityCompat}; Upset Stomach also marks the villager sick.
     */
    @Override
    public boolean applyWaterSickness(LivingEntity drinker, ItemStack stack, RandomSource random) {
        if (!isPurityWaterContainer(stack) || sicknessEffects == null || grades == null) return false;
        int grade = purity(stack);
        if (grade < 0 || grade >= grades.length) return false;
        try {
            Object config = config();
            if (config == null || !(sicknessEffects.get(config) instanceof Map<?, ?> byDifficulty)) return false;
            Object key = sicknessKey.invoke(null, drinker.level().getDifficulty());
            if (!(byDifficulty.get(key) instanceof Map<?, ?> byGrade)) return true;
            if (!(byGrade.get(grades[grade]) instanceof List<?> lines) || lines.isEmpty()) return true;
            float[] rolls = new float[lines.size()];
            boolean ill = false;
            for (int i = 0; i < lines.size(); i++) {
                Object line = lines.get(i);
                rolls[i] = sharedRoll(lines, rolls, i, random);
                if (rolls[i] * 100f >= lineChance.getInt(line)) continue;
                if (!(sicknessHolder.invoke(line) instanceof Holder<?> holder)) continue;
                int ticks = lineSeconds.getInt(line) * 20;
                int amplifier = Math.max(0, lineLevel.getInt(line) - 1);
                @SuppressWarnings("unchecked") Holder<MobEffect> effect = (Holder<MobEffect>) holder;
                //? if >=1.21 {
                drinker.addEffect(new MobEffectInstance(effect, ticks, amplifier));
                //?} else {
                /*drinker.addEffect(new MobEffectInstance(effect.value(), ticks, amplifier));
                *///?}
                ill = true;
            }
            if (ill && drinker instanceof VillagerEntityMCA villager) McaSicknessAdapter.markSick(villager, false);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private float sharedRoll(List<?> lines, float[] rolls, int index, RandomSource random)
            throws IllegalAccessException {
        Object group = lineGroup.get(lines.get(index));
        if (group != null) {
            for (int earlier = 0; earlier < index; earlier++) {
                if (group.equals(lineGroup.get(lines.get(earlier)))) return rolls[earlier];
            }
        }
        return random.nextFloat();
    }

    @Override
    public float exhaustionBiomeModifier(Level level, BlockPos pos) {
        return exhaustionBiomeModifier(level, pos, ThermalHydrationContext.NONE);
    }

    /**
     * The mod's climate curve on the temperature it would see: Townstead's ambient (in biome units)
     * while temperature runs, the same reading its player climate is fed, else the biome base.
     */
    @Override
    public float exhaustionBiomeModifier(Level level, BlockPos pos, ThermalHydrationContext thermal) {
        if (level == null || pos == null || !isActive()) return 1f;
        float scale = 1f;
        Object config = config();
        try {
            if (config != null && depletion != null) scale = (float) depletion.getDouble(config);
        } catch (IllegalAccessException ignored) {}
        if (level.dimensionType().ultraWarm()) return Math.max(0f, scale * NETHER_MODIFIER);
        var biome = level.getBiome(pos).value();
        float temperature = biome.getBaseTemperature();
        if (TownsteadConfig.temperatureActive() && level instanceof ServerLevel server) {
            float celsius = com.aetherianartificer.townstead.temperature.TemperatureData.ambientCelsius(server, pos);
            if (Float.isFinite(celsius)) temperature = celsius / 25f;
        }
        return Math.max(0f, scale * climateFactor(temperature, biome.hasPrecipitation()));
    }

    private float climateFactor(float temperature, boolean wet) {
        if (call(climateFactor, temperature, wet) instanceof Float factor && Float.isFinite(factor)) return factor;
        float factor = 0.8f + 0.25f * (temperature - 0.5f) + (wet ? 0f : 0.15f);
        return Math.max(0.65f, Math.min(1.35f, factor));
    }

    /** The grade of a cauldron or pot, read the way the mod reads it when water is drawn. */
    @Override
    public Object sampleBlockWater(ServerLevel level, BlockPos pos) {
        return isActive() ? call(sampleAt, level, pos) : null;
    }

    @Override
    public ItemStack gradeServing(ItemStack serving, Object sample) {
        if (sample == null || setQuality == null || !flag(isWaterContainer, serving) || flag(isStamped, serving)) return serving;
        return call(setQuality, serving, sample) instanceof ItemStack graded ? graded : serving;
    }

    @Override
    public boolean extraHydrationToQuenched() {
        return false;
    }

    @Override
    public ResourceLocation iconTexture() {
        return ICONS;
    }

    @Override
    public ThirstIconInfo iconInfo(int thirst) {
        int u = thirst > 13 ? 32 : thirst > 6 ? 16 : 0;
        return new ThirstIconInfo(ICONS, u, 0, 41, 9);
    }

    @Override
    public boolean supportsPurification() {
        return true;
    }

    /** A plain water bottle below Clean. Carried vessels boil by the mod's own campfire rule. */
    @Override
    public boolean canBoil(ItemStack stack) {
        return boilable(stack);
    }

    public static boolean boilable(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.POTION) || !INSTANCE.isPurityWaterContainer(stack)) return false;
        int grade = INSTANCE.purity(stack);
        return grade >= 0 && grade < CLEAN;
    }

    /** A Clean water bottle, stamped by the mod itself. */
    public ItemStack cleanBottle() {
        return call(waterBottle, CLEAN) instanceof ItemStack bottle ? bottle : ItemStack.EMPTY;
    }

    @Override
    public ResourceLocation purificationOutput() {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.POTION);
    }

    /** One serving out of a waterskin, canteen or flask; the vessel stays in the inventory. */
    @Override
    public ItemStack onDrinkConsumed(ItemStack stack) {
        if (!isActive() || !flag(skinIs, stack)) return ItemStack.EMPTY;
        call(skinRemove, stack, 1);
        return stack;
    }

    @Override
    public double playerThirst(Player player) {
        if (player == null || !isActive()) return Double.NaN;
        return call(playerThirst, player) instanceof Integer value ? value : Double.NaN;
    }

    @Override
    public boolean restorePlayerThirst(Player player, int immediate, int lasting) {
        if (player == null || player.level().isClientSide || !isActive()) return false;
        if (immediate <= 0 && lasting <= 0) return false;
        if (!Boolean.TRUE.equals(call(playerEnabled, player))) return false;
        try {
            playerDrink.invoke(null, player, Math.max(0, immediate), Math.max(0, lasting));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
