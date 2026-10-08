package com.aetherianartificer.townstead.work.producer;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.StationType;

import com.aetherianartificer.townstead.work.recipe.WorkIngredients;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.station.StationDropOutputs;
import com.aetherianartificer.townstead.work.station.StationContents;
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
import java.util.function.Predicate;

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
        List<ItemStack> drops = StationDropOutputs.collectWithinWorksite(
                level, stationAnchor, outputIds, worksiteBounds);
        if (drops.isEmpty()) return false;
        boolean allCarried = true;
        for (ItemStack drop : drops) {
            allCarried &= storeOutput(level, villager, drop, stationAnchor, worksiteBounds);
        }
        return allCarried;
    }

    public static boolean hotStationOutputCollectible(
            ServerLevel level,
            @Nullable BlockPos stationAnchor,
            @Nullable DiscoveredRecipe activeRecipe
    ) {
        if (stationAnchor == null || activeRecipe == null) return false;
        Item outputItem = BuiltInRegistries.ITEM.get(activeRecipe.output());
        if (outputItem == Items.AIR) return false;
        if (StationContents.count(level, stationAnchor, outputItem) < activeRecipe.outputCount()) return false;
        return StationContents.canExtract(level, stationAnchor, outputItem, activeRecipe.outputCount());
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
                int extracted = StationContents.extract(level, stationAnchor, outputItem, activeRecipe.outputCount());
                if (extracted > 0) {
                    collected = storeOutput(level, villager,
                            new ItemStack(outputItem, extracted), stationAnchor, worksiteBounds);
                    if (!collected) return new CollectResult(false, true);
                } else if (waitWhenExactOutputMissing) {
                    return new CollectResult(false, true);
                }
            }
        }

        List<ItemStack> outputs = StationContents.extractMatching(level, stationAnchor, outputIds);
        for (ItemStack output : outputs) {
            if (!storeOutput(level, villager, output, stationAnchor, worksiteBounds)) {
                return new CollectResult(false, true);
            }
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

        // Existing inventory outputs remain in hand for ProducerWorkTask.DELIVER.
    }

    public static boolean sweepWorksiteOutputs(
            ServerLevel level,
            VillagerEntityMCA villager,
            @Nullable BlockPos watchedStation,
            @Nullable ResourceLocation watchedOutput,
            Set<Long> worksiteBounds,
            Set<ResourceLocation> outputIds
    ) {
        if (outputIds.isEmpty()) return false;
        return sweepWorksiteItems(
                level, villager, watchedStation, watchedOutput, worksiteBounds,
                stack -> {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    return id != null && outputIds.contains(id);
                });
    }

    /** Shared worksite housekeeping with a trade-owned definition of finished goods. */
    public static boolean sweepWorksiteItems(
            ServerLevel level,
            VillagerEntityMCA villager,
            @Nullable BlockPos watchedStation,
            @Nullable ResourceLocation watchedOutput,
            Set<Long> worksiteBounds,
            Predicate<ItemStack> finishedGood
    ) {
        if (finishedGood == null || worksiteBounds == null || worksiteBounds.isEmpty()) return false;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long packed : worksiteBounds) {
            BlockPos pos = BlockPos.of(packed);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        AABB area = new AABB(minX, minY - 3, minZ, maxX + 1, maxY + 4, maxZ + 1);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area, entity -> {
            if (!StationDropOutputs.insideWorksiteColumn(entity.blockPosition(), worksiteBounds)) return false;
            ItemStack stack = entity.getItem();
            return !stack.isEmpty() && finishedGood.test(stack);
        });
        if (drops.isEmpty()) return false;
        boolean sweptWatchedOutput = false;
        AABB watchedArea = watchedStation == null ? null : new AABB(watchedStation).inflate(3.0, 2.0, 3.0);
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem().copy();
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (watchedArea != null && watchedOutput != null && watchedOutput.equals(id)
                    && watchedArea.contains(drop.position())) {
                sweptWatchedOutput = true;
            }
            BlockPos dropPos = drop.blockPosition();
            drop.discard();
            if (!storeOutput(level, villager, stack, dropPos, worksiteBounds)) {
                sweptWatchedOutput = false;
            }
        }
        return sweptWatchedOutput;
    }

    public static boolean storeOutput(
            ServerLevel level,
            VillagerEntityMCA villager,
            ItemStack output,
            @Nullable BlockPos stationAnchor,
            Set<Long> worksiteBounds
    ) {
        if (!output.isEmpty()) {
            ItemStack remainder = villager.getInventory().addItem(output);
            if (!remainder.isEmpty()) {
                ItemEntity entity = new ItemEntity(level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder);
                entity.setPickUpDelay(0);
                level.addFreshEntity(entity);
                return false;
            }
        }
        return true;
    }
}
