package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.TownsteadConfig;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public final class ButcherSupplyManager {
    private static final int SEARCH_RADIUS = 16;
    private static final int VERTICAL_RADIUS = 3;
    private static final int FOOD_RESERVE_COUNT = 4;

    private ButcherSupplyManager() {}

    public static boolean hasRawInput(SimpleContainer inv, ServerLevel level, int tier) {
        return findRawInputSlot(inv, level, tier) >= 0;
    }

    public static boolean hasFuel(SimpleContainer inv) {
        return findFuelSlot(inv) >= 0;
    }

    public static int findRawInputSlot(SimpleContainer inv, ServerLevel level, int tier) {
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!isRawInput(stack, level, tier)) continue;
            int score = rawInputScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    public static int findFuelSlot(SimpleContainer inv) {
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!isFuel(stack)) continue;
            int score = fuelScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    public static boolean pullRawInput(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor, int tier) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level,
                villager,
                SEARCH_RADIUS,
                VERTICAL_RADIUS,
                stack -> isRawInput(stack, level, tier),
                ButcherSupplyManager::rawInputScore,
                anchor
        );
    }

    public static boolean pullFuel(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level,
                villager,
                SEARCH_RADIUS,
                VERTICAL_RADIUS,
                ButcherSupplyManager::isFuel,
                ButcherSupplyManager::fuelScore,
                anchor
        );
    }

    public static boolean hasStockableOutput(SimpleContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isButcherOutput(inv.getItem(i))) return true;
        }
        return false;
    }

    public static boolean offloadOutput(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        if (anchor == null) return false;
        boolean movedAny = false;
        SimpleContainer inv = villager.getInventory();
        int reserveFoodSlot = findBestFoodSlot(inv, true);
        int reserveFuelSlot = findFuelSlot(inv);
        int reserveInputSlot = findRawInputSlot(inv, level, 5);

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!isButcherOutput(stack)) continue;
            if (i == reserveFuelSlot || i == reserveInputSlot) continue;
            if (i == reserveFoodSlot && stack.getCount() <= FOOD_RESERVE_COUNT) continue;

            int moveCount = stack.getCount();
            if (i == reserveFoodSlot) {
                moveCount = Math.max(0, stack.getCount() - FOOD_RESERVE_COUNT);
            }
            if (moveCount <= 0) continue;

            ItemStack moving = stack.copyWithCount(moveCount);
            boolean fullyStored = NearbyItemSources.insertIntoNearbyStorage(
                    level,
                    villager,
                    moving,
                    SEARCH_RADIUS,
                    VERTICAL_RADIUS,
                    anchor
            );
            if (!fullyStored && moving.getCount() == stack.getCount()) continue;
            if (i == reserveFoodSlot) {
                stack.setCount(FOOD_RESERVE_COUNT + moving.getCount());
            } else {
                stack.setCount(moving.getCount());
            }
            movedAny = true;
        }

        return movedAny;
    }

    public static boolean isRawInput(ItemStack stack, ServerLevel level, int tier) {
        if (stack.isEmpty()) return false;
        if (isButcherOutput(stack)) return false;
        if (!isUnlockedForTier(stack, tier)) return false;
        return level.getRecipeManager().getRecipeFor(
                RecipeType.SMOKING,
                new SingleRecipeInput(stack),
                level
        ).isPresent();
    }

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && AbstractFurnaceBlockEntity.isFuel(stack);
    }

    public static boolean isButcherOutput(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(Items.COOKED_BEEF)
                || stack.is(Items.COOKED_PORKCHOP)
                || stack.is(Items.COOKED_MUTTON)
                || stack.is(Items.COOKED_CHICKEN)
                || stack.is(Items.COOKED_RABBIT)
                || stack.is(Items.COOKED_COD)
                || stack.is(Items.COOKED_SALMON);
    }

    private static boolean isPreferredRawFood(ItemStack stack) {
        return stack.is(Items.BEEF)
                || stack.is(Items.PORKCHOP)
                || stack.is(Items.MUTTON)
                || stack.is(Items.CHICKEN)
                || stack.is(Items.RABBIT)
                || stack.is(Items.COD)
                || stack.is(Items.SALMON)
                || stack.is(Items.TROPICAL_FISH);
    }

    private static boolean isUnlockedForTier(ItemStack stack, int tier) {
        int normalizedTier = Math.max(1, Math.min(tier, 5));
        if (normalizedTier >= 5) return true;
        if (stack.is(Items.CHICKEN) || stack.is(Items.RABBIT) || stack.is(Items.COD) || stack.is(Items.SALMON)) {
            return true;
        }
        if (normalizedTier >= 2 && (stack.is(Items.PORKCHOP) || stack.is(Items.TROPICAL_FISH))) {
            return true;
        }
        if (normalizedTier >= 3 && stack.is(Items.MUTTON)) {
            return true;
        }
        return normalizedTier >= 4 && stack.is(Items.BEEF);
    }

    private static int rawInputScore(ItemStack stack) {
        int score = stack.getCount();
        if (stack.is(Items.BEEF) || stack.is(Items.PORKCHOP) || stack.is(Items.MUTTON)) score += 100;
        if (stack.is(Items.CHICKEN) || stack.is(Items.RABBIT)) score += 80;
        if (stack.is(Items.COD) || stack.is(Items.SALMON) || stack.is(Items.TROPICAL_FISH)) score += 60;
        return score;
    }

    private static int fuelScore(ItemStack stack) {
        int score = stack.getCount();
        if (stack.is(Items.CHARCOAL)) return score + 500;
        if (stack.is(Items.COAL)) return score + 450;
        if (stack.is(Items.BLAZE_ROD)) return score + 300;
        if (stack.is(Items.DRIED_KELP_BLOCK)) return score + 200;
        if (stack.is(Items.COAL_BLOCK)) return score + 50;
        return score;
    }

    private static int findBestFoodSlot(SimpleContainer inv, boolean excludeButcherOutput) {
        int bestSlot = -1;
        int bestNutrition = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (excludeButcherOutput && isButcherOutput(stack)) continue;
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null || food.nutrition() <= 0) continue;
            if (food.nutrition() > bestNutrition) {
                bestNutrition = food.nutrition();
                bestSlot = i;
            }
        }
        return bestSlot;
    }
}
