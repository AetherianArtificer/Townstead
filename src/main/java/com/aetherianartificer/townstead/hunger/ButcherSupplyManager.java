package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.hunger.profile.ButcherProfileDefinition;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
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

    public static boolean hasRawInput(SimpleContainer inv, ServerLevel level, int tier, ButcherProfileDefinition profile) {
        return findRawInputSlot(inv, level, tier, profile) >= 0;
    }

    public static boolean hasFuel(SimpleContainer inv, ButcherProfileDefinition profile) {
        return findFuelSlot(inv, profile) >= 0;
    }

    public static int findRawInputSlot(SimpleContainer inv, ServerLevel level, int tier, ButcherProfileDefinition profile) {
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!isRawInput(stack, level, tier, profile)) continue;
            int score = rawInputScore(stack, profile);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    public static int findFuelSlot(SimpleContainer inv, ButcherProfileDefinition profile) {
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!isFuel(stack, profile)) continue;
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
            BlockPos anchor,
            int tier,
            ButcherProfileDefinition profile
    ) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level,
                villager,
                SEARCH_RADIUS,
                VERTICAL_RADIUS,
                stack -> isRawInput(stack, level, tier, profile),
                stack -> rawInputScore(stack, profile),
                anchor
        );
    }

    public static boolean pullFuel(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor, ButcherProfileDefinition profile) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level,
                villager,
                SEARCH_RADIUS,
                VERTICAL_RADIUS,
                stack -> isFuel(stack, profile),
                ButcherSupplyManager::fuelScore,
                anchor
        );
    }

    public static boolean hasStockableOutput(SimpleContainer inv, ButcherProfileDefinition profile) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isButcherOutput(inv.getItem(i), profile)) return true;
        }
        return false;
    }

    public static boolean offloadOutput(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos anchor,
            int tier,
            ButcherProfileDefinition profile
    ) {
        if (anchor == null) return false;
        boolean movedAny = false;
        SimpleContainer inv = villager.getInventory();
        int reserveFoodSlot = findBestFoodSlot(inv, true);
        int reserveFuelSlot = findFuelSlot(inv, profile);
        int reserveInputSlot = findRawInputSlot(inv, level, tier, profile);

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!isButcherOutput(stack, profile)) continue;
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

    public static boolean isRawInput(ItemStack stack, ServerLevel level, int tier, ButcherProfileDefinition profile) {
        if (stack.isEmpty()) return false;
        if (isButcherOutput(stack, profile)) return false;
        if (profile != null && profile.hasInputFilters()) {
            if (!profile.matchesInput(stack)) return false;
        } else if (!isUnlockedForTier(stack, tier)) {
            return false;
        }
        var recipe = level.getRecipeManager().getRecipeFor(
                RecipeType.SMOKING,
                new SingleRecipeInput(stack),
                level
        );
        if (recipe.isEmpty()) return false;

        ItemStack output = recipe.get().value().assemble(new SingleRecipeInput(stack), level.registryAccess());
        if (output.isEmpty()) return false;
        // Guard against no-op or loop recipes (input -> same input) to avoid deadlocks.
        if (ItemStack.isSameItemSameComponents(output, stack)) return false;
        // If profile declares output rules, require the smoked result to match those rules.
        if (profile != null && profile.hasOutputFilters() && !profile.matchesOutput(output)) return false;
        return true;
    }

    public static boolean isFuel(ItemStack stack, ButcherProfileDefinition profile) {
        if (stack.isEmpty()) return false;
        if (!AbstractFurnaceBlockEntity.isFuel(stack)) return false;
        if (profile != null && profile.hasFuelFilters()) {
            return profile.matchesFuel(stack);
        }
        return true;
    }

    public static boolean isButcherOutput(ItemStack stack, ButcherProfileDefinition profile) {
        if (stack.isEmpty()) return false;
        if (profile != null && profile.hasOutputFilters() && profile.matchesOutput(stack)) {
            return true;
        }
        return stack.is(Items.COOKED_BEEF)
                || stack.is(Items.COOKED_PORKCHOP)
                || stack.is(Items.COOKED_MUTTON)
                || stack.is(Items.COOKED_CHICKEN)
                || stack.is(Items.COOKED_RABBIT)
                || stack.is(Items.COOKED_COD)
                || stack.is(Items.COOKED_SALMON);
    }

    public static boolean isValidSmokerInput(ItemStack stack, ServerLevel level, int tier, ButcherProfileDefinition profile) {
        if (stack.isEmpty()) return true;
        return isRawInput(stack, level, tier, profile);
    }

    public static boolean isSmokerBlockerInput(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) return false;
        return level.getRecipeManager().getRecipeFor(
                RecipeType.SMOKING,
                new SingleRecipeInput(stack),
                level
        ).isEmpty();
    }

    public static boolean hasUsableSmokingRecipe(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) return false;
        var recipe = level.getRecipeManager().getRecipeFor(
                RecipeType.SMOKING,
                new SingleRecipeInput(stack),
                level
        );
        if (recipe.isEmpty()) return false;
        ItemStack output = recipe.get().value().assemble(new SingleRecipeInput(stack), level.registryAccess());
        if (output.isEmpty()) return false;
        return !ItemStack.isSameItemSameComponents(output, stack);
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

    private static int rawInputScore(ItemStack stack, ButcherProfileDefinition profile) {
        int score = stack.getCount();
        if (stack.is(Items.BEEF) || stack.is(Items.PORKCHOP) || stack.is(Items.MUTTON)) score += 100;
        if (stack.is(Items.CHICKEN) || stack.is(Items.RABBIT)) score += 80;
        if (stack.is(Items.COD) || stack.is(Items.SALMON) || stack.is(Items.TROPICAL_FISH)) score += 60;
        if (profile != null && profile.hasInputFilters()) {
            ResourceLocation key = stack.getItem().builtInRegistryHolder().key().location();
            if (profile.inputItems().contains(key)) score += 20;
        }
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
            if (excludeButcherOutput && isButcherOutput(stack, null)) continue;
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
