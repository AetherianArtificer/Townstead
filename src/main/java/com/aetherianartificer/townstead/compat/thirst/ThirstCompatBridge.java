package com.aetherianartificer.townstead.compat.thirst;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface ThirstCompatBridge {

    /**
     * The top step of the bridge purity scale: water that is finished, whatever the mod calls
     * it. Below this a container is purification input; at it, purification output.
     */
    int PURITY_PURIFIED = 3;

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

    /**
     * The item an ordered purification line wears and counts, or null for the vanilla water
     * bottle. What a village purifies water <em>in</em> is the thirst mod's own idiom: bottles
     * under Thirst Was Taken and Thirst Was Reclaimed, canteens under Legendary Survival
     * Overhaul. The work itself keeps taking any impure container the bridge recognises.
     */
    default ResourceLocation purificationOutput() { return null; }

    default void purifyResult(ItemStack input, ItemStack output) {}

    /**
     * Called after a villager consumes a drink. Mutates the stack in-place to reflect
     * consumption (e.g. reduce canteen capacity). Returns the remainder item that should
     * stay in inventory, or {@link ItemStack#EMPTY} if the item is fully consumed
     * (normal shrink behavior).
     */
    default ItemStack onDrinkConsumed(ItemStack drinkStack) { return ItemStack.EMPTY; }

    ThirstIconInfo iconInfo(int thirst);

    /** Current player thirst, or NaN when the active compat cannot read it. */
    default double playerThirst(Player player) { return Double.NaN; }

    record PurityResult(boolean applyHydration, boolean sickness, boolean poison, int purity) {}

    record ThirstIconInfo(ResourceLocation texture, int u, int v, int texW, int texH) {}
}
