package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * TerraFirmaCraft farmland nutrients, by reflection. Which items fertilize and what they add
 * come from TFC's own fertilizer data ({@code Fertilizer.get}); the soil bonus from TFC's
 * {@code CropHelpers.getSoilModifier} where it exists (1.21). The Fertilizer class moved from
 * {@code util} to {@code util.data} between versions; both are probed.
 */
public final class TfcNutrientCompat implements FarmerNutrientCompat {
    /** A cell asks for feeding while any nutrient sits below this. */
    private static final float FEED_BELOW = 0.9f;

    private static volatile boolean probed;
    private static Class<?> farmlandType;
    private static Method getNutrient;
    private static Method addNutrients;
    private static Method fertilizerGet;
    private static Method fertilizerNutrient;
    private static Object[] nutrientTypes;
    @Nullable private static Method soilModifier;
    @Nullable private static Method addParticles;
    @Nullable private static Item cachedIcon;
    private static boolean iconResolved;

    @Override
    public String modId() {
        return "tfc";
    }

    @Override
    public boolean wantsFeeding(ServerLevel level, BlockPos soilPos) {
        Object farmland = farmland(level, soilPos);
        if (farmland == null) return false;
        try {
            for (Object type : nutrientTypes) {
                if (((Number) getNutrient.invoke(farmland, type)).floatValue() < FEED_BELOW) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Override
    public boolean isFertilizer(ItemStack stack) {
        return fertilizer(stack) != null;
    }

    @Override
    public boolean wouldHelp(ServerLevel level, BlockPos soilPos, ItemStack stack) {
        Object farmland = farmland(level, soilPos);
        Object fertilizer = fertilizer(stack);
        if (farmland == null || fertilizer == null) return false;
        float bonus = soilBonus(level.getBlockState(soilPos));
        try {
            for (Object type : nutrientTypes) {
                float dose = ((Number) fertilizerNutrient.invoke(fertilizer, type)).floatValue() * bonus;
                if (dose <= 0) continue;
                float current = ((Number) getNutrient.invoke(farmland, type)).floatValue();
                if (current + dose * 0.5f <= 1f) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Override
    public boolean feed(ServerLevel level, BlockPos soilPos, ItemStack stack) {
        Object farmland = farmland(level, soilPos);
        Object fertilizer = fertilizer(stack);
        if (farmland == null || fertilizer == null) return false;
        try {
            addNutrients.invoke(farmland, fertilizer, soilBonus(level.getBlockState(soilPos)));
            if (addParticles != null) addParticles.invoke(null, level, soilPos.above(), fertilizer);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The most balanced fertilizer TFC knows (compost in the base game), read from its data. */
    @Override
    @Nullable
    public Item icon() {
        if (iconResolved) return cachedIcon;
        if (!probe()) return null;
        Item best = null;
        float bestMin = -1f;
        float bestSum = -1f;
        for (Item item : BuiltInRegistries.ITEM) {
            Object fertilizer = fertilizer(new ItemStack(item));
            if (fertilizer == null) continue;
            try {
                float min = Float.MAX_VALUE;
                float sum = 0f;
                for (Object type : nutrientTypes) {
                    float v = ((Number) fertilizerNutrient.invoke(fertilizer, type)).floatValue();
                    min = Math.min(min, v);
                    sum += v;
                }
                if (min > bestMin || (min == bestMin && sum > bestSum)) {
                    best = item;
                    bestMin = min;
                    bestSum = sum;
                }
            } catch (Throwable ignored) {
            }
        }
        // Fertilizer data arrives with the world; before that there is nothing to pick yet.
        if (best != null) {
            cachedIcon = best;
            iconResolved = true;
        }
        return best;
    }

    @Nullable
    private static Object farmland(ServerLevel level, BlockPos pos) {
        if (!probe()) return null;
        BlockEntity be = level.getBlockEntity(pos);
        return farmlandType.isInstance(be) ? be : null;
    }

    @Nullable
    private static Object fertilizer(ItemStack stack) {
        if (stack.isEmpty() || !probe()) return null;
        try {
            return fertilizerGet.invoke(null, stack);
        } catch (Throwable t) {
            return null;
        }
    }

    private static float soilBonus(BlockState state) {
        if (soilModifier == null) return 1f;
        try {
            return ((Number) soilModifier.invoke(null, state)).floatValue();
        } catch (Throwable t) {
            return 1f;
        }
    }

    private static synchronized boolean probe() {
        if (probed) return getNutrient != null;
        probed = true;
        try {
            Class<?> nutrientType = Class.forName("net.dries007.tfc.common.blockentities.FarmlandBlockEntity$NutrientType");
            Class<?> fertilizerType = classOrNull("net.dries007.tfc.util.data.Fertilizer");
            if (fertilizerType == null) fertilizerType = Class.forName("net.dries007.tfc.util.Fertilizer");
            Class<?> farmland = Class.forName("net.dries007.tfc.common.blockentities.IFarmland");
            Method nutrient = farmland.getMethod("getNutrient", nutrientType);
            Method add = farmland.getMethod("addNutrients", fertilizerType, float.class);
            Method get = fertilizerType.getMethod("get", ItemStack.class);
            Method fertNutrient = fertilizerType.getMethod("getNutrient", nutrientType);
            nutrientTypes = nutrientType.getEnumConstants();
            fertilizerGet = get;
            fertilizerNutrient = fertNutrient;
            addNutrients = add;
            farmlandType = farmland;
            try {
                addParticles = farmland.getMethod("addNutrientParticles", ServerLevel.class, BlockPos.class, fertilizerType);
            } catch (NoSuchMethodException ignored) {
            }
            Class<?> cropHelpers = classOrNull("net.dries007.tfc.common.blocks.crop.CropHelpers");
            if (cropHelpers != null) {
                try {
                    soilModifier = cropHelpers.getMethod("getSoilModifier", BlockState.class);
                } catch (NoSuchMethodException ignored) {
                }
            }
            getNutrient = nutrient;
            return true;
        } catch (Throwable t) {
            Townstead.LOGGER.warn("TFC nutrient compat unavailable: {}", t.toString());
            return false;
        }
    }

    @Nullable
    private static Class<?> classOrNull(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
