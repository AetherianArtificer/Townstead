package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ThirstWasTakenBridge implements ThirstCompatBridge {
    public static final ThirstWasTakenBridge INSTANCE = new ThirstWasTakenBridge();
    //? if >=1.21 {
    private static final ResourceLocation THIRST_ICONS =
            ResourceLocation.fromNamespaceAndPath("thirst", "textures/gui/thirst_icons.png");
    //?} else {
    /*private static final ResourceLocation THIRST_ICONS =
            new ResourceLocation("thirst", "textures/gui/thirst_icons.png");
    *///?}

    private static final int FALLBACK_DEFAULT_PURITY = 2;
    private static final int FALLBACK_DIRTY_NAUSEA = 100;
    private static final int FALLBACK_DIRTY_POISON = 30;
    private static final int FALLBACK_SLIGHTLY_DIRTY_NAUSEA = 50;
    private static final int FALLBACK_SLIGHTLY_DIRTY_POISON = 10;
    private static final int FALLBACK_ACCEPTABLE_NAUSEA = 5;
    private static final int FALLBACK_ACCEPTABLE_POISON = 0;
    private static final int FALLBACK_PURIFIED_NAUSEA = 0;
    private static final int FALLBACK_PURIFIED_POISON = 0;
    private static final boolean FALLBACK_QUENCH_WHEN_DEBUFFED = true;
    private static final boolean FALLBACK_EXTRA_HYDRATION_TO_QUENCHED = true;
    private static final float FALLBACK_THIRST_DEPLETION_MODIFIER = 1.2f;
    private static final float FALLBACK_NETHER_THIRST_DEPLETION_MODIFIER = 3.0f;
    private static final float MODIFIER_HARSHNESS = 0.5f;

    private boolean initialized;
    private boolean active;
    private Method itemRestoresThirstMethod;
    private Method isDrinkMethod;
    private Method isPurityWaterContainerMethod;
    private Method getThirstMethod;
    private Method getQuenchedMethod;
    private Method getPurityMethod;
    private Class<?> commonConfigClass;

    private ThirstWasTakenBridge() {}

    @Override
    public boolean isActive() {
        initIfNeeded();
        return active;
    }

    @Override
    public boolean itemRestoresThirst(ItemStack stack) {
        if (stack.isEmpty()) return false;
        initIfNeeded();
        if (!active || itemRestoresThirstMethod == null) return false;
        try {
            return Boolean.TRUE.equals(itemRestoresThirstMethod.invoke(null, stack));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isDrink(ItemStack stack) {
        if (stack.isEmpty()) return false;
        initIfNeeded();
        if (!active || isDrinkMethod == null) return false;
        try {
            return Boolean.TRUE.equals(isDrinkMethod.invoke(null, stack));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int hydration(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        initIfNeeded();
        if (!active || getThirstMethod == null) return 0;
        try {
            Object value = getThirstMethod.invoke(null, stack);
            if (value instanceof Number n) return n.intValue();
        } catch (Exception ignored) {}
        return 0;
    }

    @Override
    public boolean isPurityWaterContainer(ItemStack stack) {
        if (stack.isEmpty()) return false;
        initIfNeeded();
        if (!active || isPurityWaterContainerMethod == null) return false;
        try {
            return Boolean.TRUE.equals(isPurityWaterContainerMethod.invoke(null, stack));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int quenched(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        initIfNeeded();
        if (!active || getQuenchedMethod == null) return 0;
        try {
            Object value = getQuenchedMethod.invoke(null, stack);
            if (value instanceof Number n) return n.intValue();
        } catch (Exception ignored) {}
        return 0;
    }

    @Override
    public int purity(ItemStack stack) {
        if (stack.isEmpty()) return readConfigInt("DEFAULT_PURITY", FALLBACK_DEFAULT_PURITY);
        initIfNeeded();
        if (!active || getPurityMethod == null) return readConfigInt("DEFAULT_PURITY", FALLBACK_DEFAULT_PURITY);
        try {
            Object value = getPurityMethod.invoke(null, stack);
            if (value instanceof Number n) return n.intValue();
        } catch (Exception ignored) {}
        return readConfigInt("DEFAULT_PURITY", FALLBACK_DEFAULT_PURITY);
    }

    @Override
    public float exhaustionBiomeModifier(Level level, BlockPos pos) {
        if (level == null || pos == null) return 1.0f;
        initIfNeeded();
        if (!active) return 1.0f;

        if (level.dimensionType().ultraWarm()) {
            return Math.max(0.0f, readConfigFloat("NETHER_THIRST_DEPLETION_MODIFIER", FALLBACK_NETHER_THIRST_DEPLETION_MODIFIER));
        }

        float humidity = resolveBiomeDownfall(level, pos) + 0.6f;
        if (humidity <= 0.6f) {
            humidity += 0.5f;
        }

        float temp = resolveBiomeTemperature(level, pos) + 0.2f;
        if (temp <= 0.0f) {
            temp = (float) Math.exp(temp);
        } else if (temp > 1.0f) {
            temp /= 2.0f;
        }

        float depletion = readConfigFloat("THIRST_DEPLETION_MODIFIER", FALLBACK_THIRST_DEPLETION_MODIFIER);
        float modifier = depletion * (temp / Math.max(0.001f, humidity));
        if (modifier < 1.0f) {
            float offset = (1.0f - modifier) * MODIFIER_HARSHNESS;
            modifier = 1.0f - offset;
        }

        if (!Float.isFinite(modifier)) return 1.0f;
        return Math.max(0.0f, modifier);
    }

    @Override
    public boolean extraHydrationToQuenched() {
        return readConfigBoolean("EXTRA_HYDRATION_CONVERT_TO_QUENCHED", FALLBACK_EXTRA_HYDRATION_TO_QUENCHED);
    }

    @Override
    public PurityResult evaluatePurity(int purity, RandomSource random) {
        int normalizedPurity = Math.max(0, Math.min(3, purity < 0 ? readConfigInt("DEFAULT_PURITY", FALLBACK_DEFAULT_PURITY) : purity));
        float chance = random.nextFloat();

        int sicknessPct;
        int poisonPct;
        switch (normalizedPurity) {
            case 0 -> {
                sicknessPct = readConfigInt("DIRTY_NAUSEA_PERCENTAGE", FALLBACK_DIRTY_NAUSEA);
                poisonPct = readConfigInt("DIRTY_POISON_PERCENTAGE", FALLBACK_DIRTY_POISON);
            }
            case 1 -> {
                sicknessPct = readConfigInt("SLIGHTLY_DIRTY_NAUSEA_PERCENTAGE", FALLBACK_SLIGHTLY_DIRTY_NAUSEA);
                poisonPct = readConfigInt("SLIGHTLY_DIRTY_POISON_PERCENTAGE", FALLBACK_SLIGHTLY_DIRTY_POISON);
            }
            case 2 -> {
                sicknessPct = readConfigInt("ACCEPTABLE_NAUSEA_PERCENTAGE", FALLBACK_ACCEPTABLE_NAUSEA);
                poisonPct = readConfigInt("ACCEPTABLE_POISON_PERCENTAGE", FALLBACK_ACCEPTABLE_POISON);
            }
            default -> {
                sicknessPct = readConfigInt("PURIFIED_NAUSEA_PERCENTAGE", FALLBACK_PURIFIED_NAUSEA);
                poisonPct = readConfigInt("PURIFIED_POISON_PERCENTAGE", FALLBACK_PURIFIED_POISON);
            }
        }

        boolean sickness = chance < (sicknessPct / 100.0f);
        boolean poison = chance <= (poisonPct / 100.0f);
        boolean applyHydration = !poison || readConfigBoolean("QUENCH_THIRST_WHEN_DEBUFFED", FALLBACK_QUENCH_WHEN_DEBUFFED);
        return new PurityResult(applyHydration, sickness, poison, normalizedPurity);
    }

    @Override
    public ResourceLocation iconTexture() {
        return THIRST_ICONS;
    }

    private void initIfNeeded() {
        if (initialized) return;
        initialized = true;
        if (!ModCompat.isLoaded("thirst")) {
            active = false;
            return;
        }
        try {
            Class<?> thirstHelper = Class.forName("dev.ghen.thirst.api.ThirstHelper");
            commonConfigClass = Class.forName("dev.ghen.thirst.foundation.config.CommonConfig");
            itemRestoresThirstMethod = thirstHelper.getMethod("itemRestoresThirst", ItemStack.class);
            isDrinkMethod = thirstHelper.getMethod("isDrink", ItemStack.class);
            getThirstMethod = thirstHelper.getMethod("getThirst", ItemStack.class);
            getQuenchedMethod = thirstHelper.getMethod("getQuenched", ItemStack.class);
            getPurityMethod = thirstHelper.getMethod("getPurity", ItemStack.class);
            try {
                Class<?> waterPurity = Class.forName("dev.ghen.thirst.content.purity.WaterPurity");
                isPurityWaterContainerMethod = waterPurity.getMethod("isWaterFilledContainer", ItemStack.class);
            } catch (Exception ignored) {
                isPurityWaterContainerMethod = null;
            }
            active = true;
            Townstead.LOGGER.info("Thirst Was Taken compatibility enabled.");
        } catch (Exception e) {
            active = false;
            Townstead.LOGGER.warn("Failed to initialize Thirst Was Taken compatibility. Villager thirst integration disabled.", e);
        }
    }

    private int readConfigInt(String fieldName, int fallback) {
        Object value = readConfigValue(fieldName);
        if (value instanceof Number n) return n.intValue();
        return fallback;
    }

    private float readConfigFloat(String fieldName, float fallback) {
        Object value = readConfigValue(fieldName);
        if (value instanceof Number n) return n.floatValue();
        return fallback;
    }

    private boolean readConfigBoolean(String fieldName, boolean fallback) {
        Object value = readConfigValue(fieldName);
        if (value instanceof Boolean b) return b;
        return fallback;
    }

    private Object readConfigValue(String fieldName) {
        initIfNeeded();
        if (!active || commonConfigClass == null) return null;
        try {
            Field field = commonConfigClass.getField(fieldName);
            Object configEntry = field.get(null);
            if (configEntry == null) return null;
            Method getMethod = configEntry.getClass().getMethod("get");
            return getMethod.invoke(configEntry);
        } catch (Exception ignored) {
            return null;
        }
    }

    private float resolveBiomeDownfall(Level level, BlockPos pos) {
        Object biome = level.getBiome(pos).value();
        Float direct = invokeFloatNoArg(biome, "getDownfall");
        if (direct != null) return direct;

        Object climate = invokeNoArg(biome, "getModifiedClimateSettings");
        Float climateValue = invokeFloatNoArg(climate, "downfall");
        if (climateValue != null) return climateValue;
        climateValue = invokeFloatNoArg(climate, "getDownfall");
        if (climateValue != null) return climateValue;

        return 0.5f;
    }

    private float resolveBiomeTemperature(Level level, BlockPos pos) {
        Object biome = level.getBiome(pos).value();
        Float base = invokeFloatNoArg(biome, "getBaseTemperature");
        if (base != null) return base;

        Float atPos = invokeFloatBlockPosArg(biome, "getTemperature", pos);
        if (atPos != null) return atPos;

        Object climate = invokeNoArg(biome, "getModifiedClimateSettings");
        Float climateValue = invokeFloatNoArg(climate, "temperature");
        if (climateValue != null) return climateValue;
        climateValue = invokeFloatNoArg(climate, "getTemperature");
        if (climateValue != null) return climateValue;

        return 0.8f;
    }

    private Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Float invokeFloatNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            if (value instanceof Number n) return n.floatValue();
        } catch (Exception ignored) {}
        return null;
    }

    private Float invokeFloatBlockPosArg(Object target, String methodName, BlockPos pos) {
        if (target == null || pos == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName, BlockPos.class);
            Object value = method.invoke(target, pos);
            if (value instanceof Number n) return n.floatValue();
        } catch (Exception ignored) {}
        return null;
    }
}
