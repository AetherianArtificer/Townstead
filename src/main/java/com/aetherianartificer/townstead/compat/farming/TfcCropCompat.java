package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.farming.FarmerBlockUse;
import com.aetherianartificer.townstead.farming.Farmland;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
//? if neoforge {
import net.neoforged.neoforge.common.Tags;
//?} else if forge {
/*import net.minecraftforge.common.Tags;
*///?}
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;

/**
 * TerraFirmaCraft crops, by class name and reflection (no compile dependency). The crop kinds
 * that differ from a vanilla crop:
 * <ul>
 *   <li>Two-block crops (maize, beans, tomatoes...) drop their yield from the lower half only;
 *       breaking the upper half first removes the lower one with no drops.</li>
 *   <li>Climbing crops need a stick before the upper half can grow.</li>
 *   <li>Pickable crops (peppers, 1.21 tomatoes) are picked by hand and regrow.</li>
 *   <li>Spreading crops (pumpkin, melon) stay put and grow fruit blocks beside them.</li>
 *   <li>Flooded crops (rice) grow on farmland under standing water: the Paddy soil.</li>
 * </ul>
 * Picking and sticks go through {@link FarmerBlockUse}, so TFC's own handling runs.
 */
public final class TfcCropCompat implements FarmerCropCompat {
    private static volatile boolean probed;
    @Nullable private static Class<?> tfcCrop;
    @Nullable private static Class<?> doubleCrop;
    @Nullable private static Class<?> climbingCrop;
    @Nullable private static Class<?> spreadingCrop;
    @Nullable private static Class<?> floodedCrop;
    @Nullable private static Class<?> pickableCrop;
    @Nullable private static Method getFruit;
    @Nullable private static Method getClimateRange;
    @Nullable private static Method checkBoth;
    @Nullable private static Method temperature;
    @Nullable private static Method hydration;

    @Override
    public String modId() {
        return "tfc";
    }

    @Override
    public boolean isSeed(ItemStack stack) {
        return false;
    }

    @Override
    public boolean shouldPartialHarvest(BlockState state) {
        return is(pickableCrop, state) && !isTop(state)
                && state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    @Override
    public List<ItemStack> doPartialHarvest(ServerLevel level, BlockPos pos, BlockState state) {
        FarmerBlockUse.Result result = FarmerBlockUse.use(level, pos, ItemStack.EMPTY);
        return result.acted() ? result.returned() : List.of();
    }

    @Override
    public boolean isExistingFarmSoil(ServerLevel level, BlockPos pos) {
        return false;
    }

    /** Standing fresh water over farmland: where a flooded crop is planted. */
    @Override
    public boolean isPlantableSpot(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        FluidState fluid = level.getFluidState(pos);
        return state.is(Blocks.WATER) && fluid.isSource() && fluid.getType() == Fluids.WATER
                && Farmland.is(level.getBlockState(pos.below()));
    }

    @Override
    public String patternHintForSeed(ItemStack stack) {
        return is(floodedCrop, placedBlock(stack)) ? "paddy" : null;
    }

    @Override
    public boolean providesPaddy() {
        probe();
        return floodedCrop != null;
    }

    @Override
    public boolean skipsHarvest(BlockState state) {
        return (is(doubleCrop, state) && isTop(state)) || is(spreadingCrop, state);
    }

    @Override
    public boolean spreadsFruit(BlockState state) {
        return is(spreadingCrop, state);
    }

    @Override
    public boolean isSpreadFruit(ServerLevel level, BlockPos pos, BlockState state) {
        if (!probe() || getFruit == null) return false;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState neighbor = level.getBlockState(pos.relative(dir));
            if (!is(spreadingCrop, neighbor)) continue;
            try {
                if (getFruit.invoke(neighbor.getBlock()) == state.getBlock()) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    // Climbing crops take their stick through the rope hooks: an item applied to a growing crop.

    @Override
    public boolean needsRope(ServerLevel level, BlockPos pos, BlockState state) {
        return is(climbingCrop, state) && !isTop(state) && !booleanValue(state, "stick")
                && level.isEmptyBlock(pos.above());
    }

    @Override
    public boolean isRope(ItemStack stack) {
        return stack.is(Tags.Items.RODS_WOODEN);
    }

    @Override
    public boolean applyRope(ServerLevel level, BlockPos pos, BlockState state) {
        FarmerBlockUse.use(level, pos, new ItemStack(Items.STICK));
        return booleanValue(level.getBlockState(pos), "stick");
    }

    /** A TFC crop is only planted when the temperature and hydration here suit it right now. */
    @Override
    public boolean canPlantNow(ServerLevel level, BlockPos cropPos, ItemStack seed) {
        Block block = placedBlock(seed);
        if (!is(tfcCrop, block) || getClimateRange == null || checkBoth == null
                || temperature == null || hydration == null) {
            return true;
        }
        try {
            Object range = getClimateRange.invoke(block);
            float temp = ((Number) temperature.invoke(null, level, cropPos)).floatValue();
            int water = ((Number) hydration.invoke(null, level, cropPos.below())).intValue();
            return (Boolean) checkBoth.invoke(range, water, temp, false);
        } catch (Throwable t) {
            return true;
        }
    }

    @Nullable
    private static Block placedBlock(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item ? item.getBlock() : null;
    }

    private static boolean is(@Nullable Class<?> type, BlockState state) {
        return is(type, state.getBlock());
    }

    private static boolean is(@Nullable Class<?> type, @Nullable Block block) {
        return block != null && probe() && type != null && type.isInstance(block);
    }

    private static boolean isTop(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if ("part".equals(property.getName())) return "top".equals(valueName(state, property));
        }
        return false;
    }

    private static boolean booleanValue(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (name.equals(property.getName())) return "true".equals(valueName(state, property));
        }
        return false;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static synchronized boolean probe() {
        if (probed) return tfcCrop != null;
        probed = true;
        String crops = "net.dries007.tfc.common.blocks.crop.";
        tfcCrop = classOrNull(crops + "CropBlock");
        if (tfcCrop == null) return false;
        doubleCrop = classOrNull(crops + "DoubleCropBlock");
        climbingCrop = classOrNull(crops + "ClimbingCropBlock");
        spreadingCrop = classOrNull(crops + "SpreadingCropBlock");
        floodedCrop = classOrNull(crops + "FloodedCropBlock");
        // 1.21 shares picking through an interface; 1.20 has only the block class.
        pickableCrop = classOrNull(crops + "IPickableCrop");
        if (pickableCrop == null) pickableCrop = classOrNull(crops + "PickableCropBlock");
        getFruit = methodOrNull(spreadingCrop, "getFruit");
        getClimateRange = methodOrNull(tfcCrop, "getClimateRange");
        Class<?> range = classOrNull("net.dries007.tfc.util.climate.ClimateRange");
        checkBoth = methodOrNull(range, "checkBoth", int.class, float.class, boolean.class);
        Class<?> climate = classOrNull("net.dries007.tfc.util.climate.Climate");
        temperature = methodOrNull(climate, "getInstantTemperature", Level.class, BlockPos.class);
        if (temperature == null) temperature = methodOrNull(climate, "getTemperature", Level.class, BlockPos.class);
        Class<?> farmland = classOrNull("net.dries007.tfc.common.blocks.soil.FarmlandBlock");
        hydration = methodOrNull(farmland, "getInstantHydration", Level.class, BlockPos.class);
        if (hydration == null) hydration = methodOrNull(farmland, "getHydration", LevelAccessor.class, BlockPos.class);
        return true;
    }

    @Nullable
    private static Class<?> classOrNull(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static Method methodOrNull(@Nullable Class<?> owner, String name, Class<?>... params) {
        if (owner == null) return null;
        try {
            return owner.getMethod(name, params);
        } catch (Throwable t) {
            return null;
        }
    }
}
