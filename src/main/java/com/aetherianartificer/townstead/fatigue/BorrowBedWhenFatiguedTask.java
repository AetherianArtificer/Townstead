package com.aetherianartificer.townstead.fatigue;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.hunger.TargetReachabilityCache;
import com.aetherianartificer.townstead.storage.RoomOwnershipAccess;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Lets an exhausted villager borrow a spare bed without adopting it as HOME.
 * Townstead owns the temporary POI ticket, navigation, and sleep request; MCA
 * remains the sole owner of permanent HOME memory and village residency.
 */
public final class BorrowBedWhenFatiguedTask extends Behavior<VillagerEntityMCA> {
    private static final int SEARCH_RADIUS = 48;
    private static final int SEARCH_INTERVAL_TICKS = 20;
    private static final int MAX_DURATION = 600;
    private static final int BED_INTERACT_DIST_SQ = 9;
    private static final int UNREACHABLE_BED_TTL_TICKS = 60;
    private static final float WALK_SPEED = 0.5F;
    private static final Set<VillagerEntityMCA> REQUESTED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<VillagerEntityMCA> NAVIGATING =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private BlockPos candidate;
    private BlockPos borrowedBed;
    private long nextSearchTick = Long.MIN_VALUE;

    public BorrowBedWhenFatiguedTask() {
        super(ImmutableMap.of(
                MemoryModuleType.HOME, MemoryStatus.REGISTERED,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        candidate = null;
        if (!TownsteadConfig.isVillagerFatigueEnabled() || villager.isSleeping()) return false;
        if (!REQUESTED.contains(villager)) return false;
        // A HOME, whenever one exists, belongs to MCA and always wins.
        if (villager.getBrain().getMemory(MemoryModuleType.HOME).isPresent()) return false;

        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        if (needs.hasEmergencyBed()) return false;

        long gameTime = level.getGameTime();
        if (nextSearchTick == Long.MIN_VALUE) {
            nextSearchTick = gameTime + Math.floorMod(villager.getId(), SEARCH_INTERVAL_TICKS);
        }
        if (gameTime < nextSearchTick) return false;
        nextSearchTick = gameTime + SEARCH_INTERVAL_TICKS;

        long dayTime = level.getDayTime() % 24000L;
        Activity scheduledActivity = villager.getBrain().getSchedule().getActivityAt((int) dayTime);
        RestDecision decision = RestCoordinator.decide(RestCoordinator.capture(
                villager,
                needs,
                true,
                false,
                scheduledActivity,
                needs.restOverrideActive()
        ));
        if (!decision.shouldSeekBed()) return false;

        candidate = level.getPoiManager().findClosest(
                holder -> holder.is(PoiTypes.HOME),
                pos -> isBorrowableBed(level, villager, pos),
                villager.blockPosition(), SEARCH_RADIUS, PoiManager.Occupancy.HAS_SPACE
        ).map(pos -> normalizeBedHead(level, pos)).orElse(null);
        return candidate != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (candidate == null || villager.getBrain().getMemory(MemoryModuleType.HOME).isPresent()) {
            candidate = null;
            return;
        }

        if (!TargetReachabilityCache.canAttempt(level, villager, candidate)) {
            candidate = null;
            return;
        }
        var path = villager.getNavigation().createPath(candidate, 1);
        if (path == null || !path.canReach()) {
            TargetReachabilityCache.recordFailure(
                    level, villager, candidate, UNREACHABLE_BED_TTL_TICKS);
            candidate = null;
            return;
        }

        BlockPos selected = candidate;
        Optional<BlockPos> claimed = level.getPoiManager().take(
                holder -> holder.is(PoiTypes.HOME),
                (holder, pos) -> pos.equals(selected) && isBorrowableBed(level, villager, pos),
                selected, 1
        );
        candidate = null;
        if (claimed.isEmpty() || villager.getBrain().getMemory(MemoryModuleType.HOME).isPresent()) {
            claimed.ifPresent(pos -> EmergencyBedReservation.releaseIfClaimed(level, pos));
            return;
        }

        borrowedBed = normalizeBedHead(level, claimed.orElseThrow());
        if (borrowedBed == null) {
            EmergencyBedReservation.releaseIfClaimed(level, claimed.orElseThrow());
            return;
        }

        TargetReachabilityCache.clear(level, villager, borrowedBed);
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        needs.setBorrowedEmergencyBed(GlobalPos.of(level.dimension(), borrowedBed));
        TownsteadVillagers.flush(villager);
        NAVIGATING.add(villager);
        BehaviorUtils.setWalkAndLookTargetMemories(villager, borrowedBed, WALK_SPEED, 1);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (borrowedBed == null) return;
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        if (!ownsReservation(level, needs, borrowedBed)
                || villager.getBrain().getMemory(MemoryModuleType.HOME).isPresent()
                || !isBorrowableBed(level, villager, borrowedBed)) {
            doStop(level, villager, gameTime);
            return;
        }

        if (villager.distanceToSqr(
                borrowedBed.getX() + 0.5D,
                borrowedBed.getY() + 0.5D,
                borrowedBed.getZ() + 0.5D) <= BED_INTERACT_DIST_SQ) {
            villager.startSleeping(borrowedBed);
            doStop(level, villager, gameTime);
            return;
        }

        if (villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty()) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, borrowedBed, WALK_SPEED, 1);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (borrowedBed == null || villager.isSleeping() || !REQUESTED.contains(villager)) return false;
        if (villager.getBrain().getMemory(MemoryModuleType.HOME).isPresent()) return false;
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        return ownsReservation(level, needs, borrowedBed)
                && RestCoordinator.decide(RestCoordinator.capture(villager, needs, true, false))
                .shouldSeekBed();
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        NAVIGATING.remove(villager);
        if (!villager.isSleeping() && borrowedBed != null) {
            cancelReservation(level, villager, borrowedBed);
        }
        borrowedBed = null;
        candidate = null;
        nextSearchTick = Math.max(nextSearchTick, gameTime + SEARCH_INTERVAL_TICKS);
    }

    private static boolean ownsReservation(
            ServerLevel level, TownsteadVillager.Needs needs, BlockPos bed) {
        GlobalPos saved = needs.emergencyBedGlobal();
        return needs.ownsEmergencyBedPoi()
                && needs.usesDirectEmergencyBed()
                && saved != null
                && saved.dimension().equals(level.dimension())
                && saved.pos().equals(bed);
    }

    private static void cancelReservation(
            ServerLevel level, VillagerEntityMCA villager, BlockPos bed) {
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        if (!ownsReservation(level, needs, bed)) return;
        EmergencyBedReservation.releaseIfClaimed(level, bed);
        needs.clearEmergencyBed();
        TownsteadVillagers.flush(villager);
    }

    private static boolean isBorrowableBed(
            ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        BlockPos head = normalizeBedHead(level, pos);
        if (head == null || !RoomOwnershipAccess.maySleep(level, villager, head)) return false;
        BlockState state = level.getBlockState(head);
        return !state.getValue(BedBlock.OCCUPIED);
    }

    private static BlockPos normalizeBedHead(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.BEDS)
                || !state.hasProperty(BedBlock.PART)
                || !state.hasProperty(BedBlock.FACING)) return null;
        BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD
                ? pos
                : pos.relative(BedBlock.getConnectedDirection(state));
        BlockState headState = level.getBlockState(head);
        return headState.is(BlockTags.BEDS)
                && headState.hasProperty(BedBlock.PART)
                && headState.getValue(BedBlock.PART) == BedPart.HEAD
                ? head.immutable()
                : null;
    }

    public static void requestEmergencyFallback(VillagerEntityMCA villager, boolean requested) {
        if (requested) REQUESTED.add(villager);
        else REQUESTED.remove(villager);
    }

    public static void forget(VillagerEntityMCA villager) {
        REQUESTED.remove(villager);
        NAVIGATING.remove(villager);
    }

    public static boolean isNavigating(VillagerEntityMCA villager) {
        return NAVIGATING.contains(villager);
    }
}
