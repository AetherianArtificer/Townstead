package com.aetherianartificer.townstead.compat.thirst;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface ThirstCompatBridge {
    boolean isActive();
    boolean itemRestoresThirst(ItemStack stack);
    boolean isDrink(ItemStack stack);
    boolean isPurityWaterContainer(ItemStack stack);
    int hydration(ItemStack stack);
    int quenched(ItemStack stack);
    int purity(ItemStack stack);
    float exhaustionBiomeModifier(Level level, BlockPos pos);
    boolean extraHydrationToQuenched();
    PurityResult evaluatePurity(int purity, RandomSource random);
    ResourceLocation iconTexture();

    boolean supportsPurification();

    default void purifyResult(ItemStack input, ItemStack output) {}

    /**
     * Called after a villager consumes a drink. Mutates the stack in-place to reflect
     * consumption (e.g. reduce canteen capacity). Returns the remainder item that should
     * stay in inventory, or {@link ItemStack#EMPTY} if the item is fully consumed
     * (normal shrink behavior).
     */
    default ItemStack onDrinkConsumed(ItemStack drinkStack) { return ItemStack.EMPTY; }

    ThirstIconInfo iconInfo(int thirst);

    record PurityResult(boolean applyHydration, boolean sickness, boolean poison, int purity) {}

    record ThirstIconInfo(ResourceLocation texture, int u, int v, int texW, int texH) {}
}
