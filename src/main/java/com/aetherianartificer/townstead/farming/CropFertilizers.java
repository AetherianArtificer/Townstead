package com.aetherianartificer.townstead.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Bone meal and fertilizers a farmer uses on the crop in a cell painted Fertilized (Bone Meal).
 * The items come from tags: {@code townstead:crop_fertilizers} for anything that grows a crop,
 * {@code townstead:giant_crop_fertilizers} for the ones that can also grow a grown crop big, then
 * giant (Farm &amp; Charm compost on its lettuce and onions). The item is used through
 * {@link FarmerBlockUse}, so the mod's own code decides what happens.
 */
public final class CropFertilizers {
    //? if >=1.21 {
    public static final TagKey<Item> ANY = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("townstead", "crop_fertilizers"));
    public static final TagKey<Item> GIANT = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("townstead", "giant_crop_fertilizers"));
    //?} else {
    /*public static final TagKey<Item> ANY = TagKey.create(Registries.ITEM,
            new ResourceLocation("townstead", "crop_fertilizers"));
    public static final TagKey<Item> GIANT = TagKey.create(Registries.ITEM,
            new ResourceLocation("townstead", "giant_crop_fertilizers"));
    *///?}

    /** How close water has to be for a crop to grow bigger, as the mods that grow giants require. */
    private static final int WATER_RADIUS = 4;

    private CropFertilizers() {}

    public static boolean isFertilizer(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ANY) || stack.is(GIANT));
    }

    /**
     * 1 for an ordinary crop, 2 for one grown big, 3 for one grown giant. A harvest counts this many
     * times over.
     */
    public static int size(BlockState state) {
        if (flag(state, "giant")) return 3;
        if (flag(state, "big")) return 2;
        return 1;
    }

    /** Whether the crop here wants a fertilizer: still growing, or grown and able to grow bigger. */
    public static boolean wantsFertilizer(Level level, BlockPos cropPos, BlockState state) {
        if (!(state.getBlock() instanceof BonemealableBlock)) return false;
        return !grown(state) || canGrowBigger(level, cropPos, state);
    }

    /** Whether this item would do anything for the crop: any fertilizer while it grows, a giant one after. */
    public static boolean wouldHelp(Level level, BlockPos cropPos, BlockState state, ItemStack stack) {
        if (!isFertilizer(stack)) return false;
        if (!grown(state)) return true;
        return stack.is(GIANT) && canGrowBigger(level, cropPos, state);
    }

    /**
     * Whether a grown crop can still grow bigger: it has big and giant states, is not giant yet, and
     * has water close by.
     */
    public static boolean canGrowBigger(Level level, BlockPos cropPos, BlockState state) {
        if (!grown(state) || !has(state, "big") || !has(state, "giant") || flag(state, "giant")) return false;
        for (BlockPos pos : BlockPos.betweenClosed(cropPos.offset(-WATER_RADIUS, 0, -WATER_RADIUS),
                cropPos.offset(WATER_RADIUS, 1, WATER_RADIUS))) {
            if (level.getFluidState(pos).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    /**
     * Uses one of {@code stack} on the crop, taking it from the stack when it was spent. Returns
     * what the use gave back, or null when nothing happened.
     */
    public static FarmerBlockUse.Result use(ServerLevel level, BlockPos cropPos, ItemStack stack) {
        FarmerBlockUse.Result result = FarmerBlockUse.use(level, cropPos, stack.copyWithCount(1));
        if (!result.acted()) return null;
        stack.shrink(1 - Math.min(1, result.held().getCount()));
        return result;
    }

    private static boolean grown(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && property.getName().equals("age")) {
                int max = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                return state.getValue(age) >= max;
            }
        }
        return false;
    }

    private static boolean has(BlockState state, String name) {
        return state.getBlock().getStateDefinition().getProperty(name) instanceof BooleanProperty;
    }

    private static boolean flag(BlockState state, String name) {
        return state.getBlock().getStateDefinition().getProperty(name) instanceof BooleanProperty property
                && state.getValue(property);
    }
}
