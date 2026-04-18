package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.TownsteadConfig;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
//? if >=1.21 {
import net.minecraft.world.item.crafting.SingleRecipeInput;
//?}
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public final class ButcherSupplyManager {
    private static final int SEARCH_RADIUS = 16;
    private static final int VERTICAL_RADIUS = 3;
    private static final int FOOD_RESERVE_COUNT = 4;

    private ButcherSupplyManager() {}

    public static boolean hasRawInput(SimpleContainer inv, ServerLevel level) {
        return findRawInputSlot(inv, level) >= 0;
    }

    public static boolean hasFuel(SimpleContainer inv) {
        return findFuelSlot(inv) >= 0;
    }

    public static int findRawInputSlot(SimpleContainer inv, ServerLevel level) {
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!isRawInput(stack, level)) continue;
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

    public static boolean pullRawInput(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos anchor
    ) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level,
                villager,
                SEARCH_RADIUS,
                VERTICAL_RADIUS,
                stack -> isRawInput(stack, level),
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

    public static boolean offloadOutput(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos anchor
    ) {
        if (anchor == null) return false;
        boolean movedAny = false;
        SimpleContainer inv = villager.getInventory();
        int reserveFoodSlot = findBestFoodSlot(inv, true);
        int reserveFuelSlot = findFuelSlot(inv);
        int reserveInputSlot = findRawInputSlot(inv, level);

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

            //? if >=1.21 {
            ItemStack moving = stack.copyWithCount(moveCount);
            //?} else {
            /*ItemStack moving = stack.copy(); moving.setCount(moveCount);
            *///?}
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

    public static boolean isRawInput(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) return false;
        if (isButcherOutput(stack)) return false;
        //? if >=1.21 {
        var recipe = level.getRecipeManager().getRecipeFor(
                RecipeType.SMOKING,
                new SingleRecipeInput(stack),
                level
        );
        if (recipe.isEmpty()) return false;
        ItemStack output = recipe.get().value().assemble(new SingleRecipeInput(stack), level.registryAccess());
        //?} else {
        /*net.minecraft.world.SimpleContainer wrapper = new net.minecraft.world.SimpleContainer(stack);
        var recipe = level.getRecipeManager().getRecipeFor(
                RecipeType.SMOKING,
                wrapper,
                level
        );
        if (recipe.isEmpty()) return false;
        ItemStack output = recipe.get().assemble(wrapper, level.registryAccess());
        *///?}
        if (output.isEmpty()) return false;
        // Guard against no-op or loop recipes (input -> same input) to avoid deadlocks.
        //? if >=1.21 {
        if (ItemStack.isSameItemSameComponents(output, stack)) return false;
        //?} else {
        /*if (ItemStack.isSameItemSameTags(output, stack)) return false;
        *///?}
        return true;
    }

    public static boolean isFuel(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return AbstractFurnaceBlockEntity.isFuel(stack);
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

    public static boolean isValidSmokerInput(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) return true;
        return isRawInput(stack, level);
    }

    public static boolean isSmokerBlockerInput(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) return false;
        //? if >=1.21 {
        return level.getRecipeManager().getRecipeFor(
                RecipeType.SMOKING,
                new SingleRecipeInput(stack),
                level
        ).isEmpty();
        //?} else {
        /*return level.getRecipeManager().getRecipeFor(
                RecipeType.SMOKING,
                new net.minecraft.world.SimpleContainer(stack),
                level
        ).isEmpty();
        *///?}
    }

    public static boolean hasUsableSmokingRecipe(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) return false;
        //? if >=1.21 {
        var recipe = level.getRecipeManager().getRecipeFor(
                RecipeType.SMOKING,
                new SingleRecipeInput(stack),
                level
        );
        if (recipe.isEmpty()) return false;
        ItemStack output = recipe.get().value().assemble(new SingleRecipeInput(stack), level.registryAccess());
        //?} else {
        /*net.minecraft.world.SimpleContainer wrapper2 = new net.minecraft.world.SimpleContainer(stack);
        var recipe = level.getRecipeManager().getRecipeFor(
                RecipeType.SMOKING,
                wrapper2,
                level
        );
        if (recipe.isEmpty()) return false;
        ItemStack output = recipe.get().assemble(wrapper2, level.registryAccess());
        *///?}
        if (output.isEmpty()) return false;
        //? if >=1.21 {
        return !ItemStack.isSameItemSameComponents(output, stack);
        //?} else {
        /*return !ItemStack.isSameItemSameTags(output, stack);
        *///?}
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
            //? if >=1.21 {
            FoodProperties food = stack.get(DataComponents.FOOD);
            //?} else {
            /*FoodProperties food = stack.getFoodProperties(null);
            *///?}
            //? if >=1.21 {
            if (food == null || food.nutrition() <= 0) continue;
            if (food.nutrition() > bestNutrition) {
                bestNutrition = food.nutrition();
            //?} else {
            /*if (food == null || food.getNutrition() <= 0) continue;
            if (food.getNutrition() > bestNutrition) {
                bestNutrition = food.getNutrition();
            *///?}
                bestSlot = i;
            }
        }
        return bestSlot;
    }
}
