package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.compat.farmersdelight.cook.IngredientResolver;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.DiscoveredRecipe;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.StationType;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.StationHandler;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public final class ProducerOutputHelper {
    public record CollectResult(boolean collected, boolean shouldWait) {}

    private ProducerOutputHelper() {}

    public static boolean collectSurfaceDrops(
            ServerLevel level,
            VillagerEntityMCA villager,
            @Nullable BlockPos stationAnchor,
            Set<Long> worksiteBounds,
            Set<ResourceLocation> outputIds
    ) {
        if (stationAnchor == null) return false;
        List<ItemStack> drops = StationHandler.collectSurfaceCookDrops(level, stationAnchor, outputIds);
        if (drops.isEmpty()) return false;
        for (ItemStack drop : drops) {
            storeOutput(level, villager, drop, stationAnchor, worksiteBounds);
        }
        return true;
    }

    public static boolean hotStationOutputCollectible(
            ServerLevel level,
            @Nullable BlockPos stationAnchor,
            @Nullable DiscoveredRecipe activeRecipe
    ) {
        if (stationAnchor == null || activeRecipe == null) return false;
        Item outputItem = BuiltInRegistries.ITEM.get(activeRecipe.output());
        if (outputItem == Items.AIR) return false;
        if (StationHandler.countItemInStation(level, stationAnchor, outputItem) < activeRecipe.outputCount()) return false;
        return StationHandler.canExtractFromStation(level, stationAnchor, outputItem, activeRecipe.outputCount());
    }

    public static CollectResult collectHotStationOutputs(
            ServerLevel level,
            VillagerEntityMCA villager,
            @Nullable BlockPos stationAnchor,
            @Nullable DiscoveredRecipe activeRecipe,
            Set<Long> worksiteBounds,
            Set<ResourceLocation> outputIds,
            boolean waitWhenExactOutputMissing
    ) {
        if (stationAnchor == null) return new CollectResult(false, false);
        boolean collected = false;

        if (activeRecipe != null) {
            Item outputItem = BuiltInRegistries.ITEM.get(activeRecipe.output());
            if (outputItem != Items.AIR) {
                int extracted = StationHandler.extractFromStation(level, stationAnchor, outputItem, activeRecipe.outputCount());
                if (extracted > 0) {
                    storeOutput(level, villager, new ItemStack(outputItem, extracted), stationAnchor, worksiteBounds);
                    collected = true;
                } else if (waitWhenExactOutputMissing) {
                    return new CollectResult(false, true);
                }
            }
        }

        List<ItemStack> outputs = StationHandler.extractMatchingStationStacks(level, stationAnchor, outputIds);
        for (ItemStack output : outputs) {
            storeOutput(level, villager, output, stationAnchor, worksiteBounds);
            collected = true;
        }
        return new CollectResult(collected, false);
    }

    public static void finishCollectInventoryOutputs(
            ServerLevel level,
            VillagerEntityMCA villager,
            @Nullable ItemStack pendingOutput,
            @Nullable BlockPos stationAnchor,
            Set<Long> worksiteBounds,
            Set<ResourceLocation> outputIds
    ) {
        if (pendingOutput != null && !pendingOutput.isEmpty()) {
            storeOutput(level, villager, pendingOutput, stationAnchor, worksiteBounds);
        }

        net.minecraft.world.SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId != null && outputIds.contains(itemId)) {
                IngredientResolver.storeOutputInCookStorage(level, villager, stack, stationAnchor, worksiteBounds);
            }
        }
    }

    public static void sweepNearbyOutputs(
            ServerLevel level,
            VillagerEntityMCA villager,
            @Nullable BlockPos storageRef,
            Set<Long> worksiteBounds,
            Set<ResourceLocation> outputIds
    ) {
        if (outputIds.isEmpty()) return;
        AABB area = villager.getBoundingBox().inflate(3.0, 2.0, 3.0);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area, entity -> {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) return false;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id != null && outputIds.contains(id);
        });
        if (drops.isEmpty()) return;
        BlockPos effectiveStorageRef = storageRef != null ? storageRef : villager.blockPosition();
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem().copy();
            if (stack.isEmpty()) continue;
            drop.discard();
            storeOutput(level, villager, stack, effectiveStorageRef, worksiteBounds);
        }
    }

    public static void storeOutput(
            ServerLevel level,
            VillagerEntityMCA villager,
            ItemStack output,
            @Nullable BlockPos stationAnchor,
            Set<Long> worksiteBounds
    ) {
        IngredientResolver.storeOutputInCookStorage(level, villager, output, stationAnchor, worksiteBounds);
        if (!output.isEmpty()) {
            ItemStack remainder = villager.getInventory().addItem(output);
            if (!remainder.isEmpty()) {
                ItemEntity entity = new ItemEntity(level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder);
                entity.setPickUpDelay(0);
                level.addFreshEntity(entity);
            }
        }
    }
}
