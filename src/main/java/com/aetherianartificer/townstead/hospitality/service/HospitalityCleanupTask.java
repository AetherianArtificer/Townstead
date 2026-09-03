package com.aetherianartificer.townstead.hospitality.service;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Clears provider-owned dirty dishes and physically returns the resulting item to storage. */
public final class HospitalityCleanupTask extends Behavior<VillagerEntityMCA> {
    private static final float WALK_SPEED = 0.45F;
    private static final int MAX_TICKS = 600;

    private @Nullable ServiceFollowup followup;
    private @Nullable BlockPos storage;
    private Set<Long> bounds = Set.of();
    private @Nullable ResourceLocation carriedItem;

    public HospitalityCleanupTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED), MAX_TICKS);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!eligible(villager) || villager.getVillagerBrain().isPanicking()
                || villager.getLastHurtByMob() != null) return false;
        var assignment = com.aetherianartificer.townstead.work.site.ProfessionWorksites
                .resolveForWork(level, villager);
        if (assignment == null) return false;
        Set<Long> extent = com.aetherianartificer.townstead.work.site.ProfessionWorksites
                .extentOf(level, assignment);
        if (extent.isEmpty()) return false;
        return pick(level, villager, extent) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        var assignment = com.aetherianartificer.townstead.work.site.ProfessionWorksites
                .resolveForWork(level, villager);
        if (assignment == null) return;
        bounds = com.aetherianartificer.townstead.work.site.ProfessionWorksites.extentOf(level, assignment);
        followup = pick(level, villager, bounds);
        storage = null;
        carriedItem = null;
        if (followup != null) walkTo(villager, followup.position());
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        return (!bounds.isEmpty() && (followup != null || carriedItem != null))
                && !villager.getVillagerBrain().isPanicking()
                && villager.getLastHurtByMob() == null;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (carriedItem != null) {
            deposit(level, villager);
            return;
        }
        if (followup == null) return;
        BlockPos target = followup.position();
        if (villager.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5,
                target.getZ() + 0.5) > 5.0D) {
            walkTo(villager, target);
            return;
        }
        if (!hasInventoryRoom(villager, followup)) return;
        ServiceFollowupCompletion result = HospitalityServiceDelivery.completeFollowup(
                level, villager, followup);
        followup = null;
        if (result.status() != ServiceFollowupCompletion.Status.SUCCESS || result.output().isEmpty()) return;
        ItemStack output = result.output();
        carriedItem = BuiltInRegistries.ITEM.getKey(output.getItem());
        ItemStack overflow = villager.getInventory().addItem(output);
        if (!overflow.isEmpty()) villager.spawnAtLocation(overflow);
        villager.getInventory().setChanged();
        villager.swing(villager.getDominantHand());
        deposit(level, villager);
    }

    private void deposit(ServerLevel level, VillagerEntityMCA villager) {
        Predicate<ItemStack> matcher = stack -> !stack.isEmpty()
                && carriedItem.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (!com.aetherianartificer.townstead.storage.PhysicalStorageDelivery
                .hasMatching(villager, matcher)) {
            carriedItem = null;
            storage = null;
            return;
        }
        if (storage == null) {
            storage = com.aetherianartificer.townstead.storage.PhysicalStorageDelivery.findDestination(
                    level, villager, bounds, matcher, Set.of(),
                    com.aetherianartificer.townstead.storage.StorageUse.OUTPUT);
            if (storage == null) return;
        }
        if (villager.distanceToSqr(storage.getX() + 0.5, storage.getY() + 0.5,
                storage.getZ() + 0.5) > 5.0D) {
            walkTo(villager, storage);
            return;
        }
        int moved = com.aetherianartificer.townstead.storage.PhysicalStorageDelivery.depositMatchingAt(
                level, villager, storage, matcher,
                com.aetherianartificer.townstead.storage.StorageUse.OUTPUT);
        if (moved > 0) {
            carriedItem = null;
            storage = null;
        } else {
            storage = null;
        }
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        // Interruption must not strand a provider-owned dish in a now-untracked inventory slot.
        // Try nearby storage immediately; any remainder stays visible in the villager inventory
        // and will be recognized again on the next cleanup pass.
        if (carriedItem != null) {
            for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
                ItemStack stack = villager.getInventory().getItem(slot);
                if (stack.isEmpty() || !carriedItem.equals(
                        BuiltInRegistries.ITEM.getKey(stack.getItem()))) continue;
                com.aetherianartificer.townstead.storage.EmptyContainerDropoff.deposit(
                        level, villager, stack, storage);
                if (stack.isEmpty()) villager.getInventory().setItem(slot, ItemStack.EMPTY);
            }
            villager.getInventory().setChanged();
        }
        followup = null;
        storage = null;
        bounds = Set.of();
        carriedItem = null;
    }

    private static @Nullable ServiceFollowup pick(ServerLevel level, VillagerEntityMCA villager,
                                                   Set<Long> bounds) {
        List<ServiceFollowup> work = HospitalityServiceDelivery.followups(level, villager, bounds);
        return work.stream().min(Comparator.comparingDouble(candidate -> villager.distanceToSqr(
                candidate.position().getX() + 0.5, candidate.position().getY() + 0.5,
                candidate.position().getZ() + 0.5))).orElse(null);
    }

    private static boolean eligible(VillagerEntityMCA villager) {
        return com.aetherianartificer.townstead.work.WorkTaskDeclarations.permitsTask(
                villager, com.aetherianartificer.townstead.profession.def.WorkTaskTypes.COOK)
                || com.aetherianartificer.townstead.work.WorkTaskDeclarations.permitsTask(
                villager, com.aetherianartificer.townstead.profession.def.WorkTaskTypes.BREW);
    }

    private static boolean hasInventoryRoom(VillagerEntityMCA villager, ServiceFollowup followup) {
        ResourceLocation expected = ResourceLocation.tryParse(
                followup.metadata().getOrDefault("output_item", ""));
        for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
            ItemStack stack = villager.getInventory().getItem(slot);
            if (stack.isEmpty()) return true;
            if (expected != null && expected.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    && stack.getCount() < stack.getMaxStackSize()) return true;
        }
        return false;
    }

    private static void walkTo(VillagerEntityMCA villager, BlockPos target) {
        BehaviorUtils.setWalkAndLookTargetMemories(villager, target, WALK_SPEED, 1);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
    }
}
