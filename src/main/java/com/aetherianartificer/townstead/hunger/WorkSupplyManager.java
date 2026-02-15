package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.TownsteadConfig;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.neoforged.neoforge.common.Tags;

import java.util.function.Predicate;

public final class WorkSupplyManager {
    private static final int SEARCH_RADIUS = 16;
    private static final int VERTICAL_RADIUS = 4;

    private WorkSupplyManager() {}

    public static void restockForCurrentJob(ServerLevel level, VillagerEntityMCA villager, Chore job) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get()) return;
        if (job == null || job == Chore.NONE) return;

        SimpleContainer inv = villager.getInventory();
        Class<?> toolType = job.getToolType();
        boolean hasTool = toolType == null || contains(inv, stack -> toolType.isInstance(stack.getItem()));
        if (!hasTool && toolType != null) {
            NearbyItemSources.pullSingleToInventory(level, villager, SEARCH_RADIUS, VERTICAL_RADIUS,
                    stack -> toolType.isInstance(stack.getItem()),
                    stack -> 1);
        }

        if (job == Chore.HARVEST) {
            boolean hasSeed = contains(inv, WorkSupplyManager::isPlantableSeed);
            if (!hasSeed) {
                NearbyItemSources.pullSingleToInventory(level, villager, SEARCH_RADIUS, VERTICAL_RADIUS,
                        WorkSupplyManager::isPlantableSeed,
                        stack -> stack.getCount());
            }

            // Bonemeal is optional for farming; only scan occasionally.
            if (villager.tickCount % 600 == 0 && !contains(inv, stack -> stack.getItem() instanceof BoneMealItem)) {
                NearbyItemSources.pullSingleToInventory(level, villager, SEARCH_RADIUS, VERTICAL_RADIUS,
                        stack -> stack.getItem() instanceof BoneMealItem,
                        stack -> stack.getCount());
            }
            if (toolType == null && !contains(inv, stack -> stack.getItem() instanceof HoeItem)) {
                NearbyItemSources.pullSingleToInventory(level, villager, SEARCH_RADIUS, VERTICAL_RADIUS,
                        stack -> stack.getItem() instanceof HoeItem,
                        stack -> 1);
            }

            if (TownsteadConfig.ENABLE_HARVEST_OUTPUT_STORAGE.get() && villager.tickCount % 40 == 0) {
                offloadHarvestOutput(level, villager, toolType);
            }
        }
    }

    private static void offloadHarvestOutput(ServerLevel level, VillagerEntityMCA villager, Class<?> requiredToolType) {
        SimpleContainer inv = villager.getInventory();
        int reserveFoodSlot = findBestFoodSlot(inv);

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (i == reserveFoodSlot) continue;
            if (requiredToolType != null && requiredToolType.isInstance(stack.getItem())) continue;
            if (stack.getItem() instanceof HoeItem) continue;
            if (stack.getItem() instanceof BoneMealItem) continue;
            if (isPlantableSeed(stack)) continue;

            ItemStack moving = stack.copy();
            boolean fullyStored = NearbyItemSources.insertIntoNearbyStorage(level, villager, moving, SEARCH_RADIUS, VERTICAL_RADIUS);
            if (!fullyStored && moving.getCount() == stack.getCount()) continue;
            stack.setCount(moving.getCount());
        }
    }

    private static boolean contains(SimpleContainer inv, Predicate<ItemStack> matcher) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (matcher.test(inv.getItem(i))) return true;
        }
        return false;
    }

    private static boolean isPlantableSeed(ItemStack stack) {
        if (stack.is(Tags.Items.SEEDS)) return true;
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        return blockItem.getBlock() instanceof CropBlock || blockItem.getBlock() instanceof StemBlock;
    }

    private static int findBestFoodSlot(SimpleContainer inv) {
        int bestSlot = -1;
        int bestNutrition = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
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
