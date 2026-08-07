package com.aetherianartificer.townstead.fatigue;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.hunger.TargetReachabilityCache;
import com.aetherianartificer.townstead.hunger.VillagerSearchCadence;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.Optional;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Emergency fallback for a fatigued villager whose assigned HOME is unavailable.
 * The fatigue ticker requests MCA's native REST behavior for normal HOME beds;
 * this task only claims a temporary nearby bed when that native route cannot run.
 */
public class SeekBedWhenFatiguedTask extends Behavior<VillagerEntityMCA> {
    private static final String SEARCH_CADENCE_KEY = "fatigue_bed_search";
    private static final int MAX_DURATION = 600;
    private static final float WALK_SPEED = 0.5f;
    private static final int CLOSE_ENOUGH = 1;
    private static final int BED_INTERACT_DIST_SQ = 9;
    private static final int BED_SEARCH_RADIUS = 48;
    private static final long EMERGENCY_CLAIM_TTL = MAX_DURATION + 200L;
    private static final int UNREACHABLE_BED_TTL_TICKS = 60;
    private static final Set<VillagerEntityMCA> EMERGENCY_FALLBACK_REQUESTED =
            Collections.newSetFromMap(new WeakHashMap<>());

    private BlockPos bedPos;
    private BlockPos emergencyClaimPos;
    private int cooldown;

    public SeekBedWhenFatiguedTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!TownsteadConfig.isVillagerFatigueEnabled()) return false;
        // The fatigue ticker performs the full rest decision once and lets MCA
        // handle an assigned HOME. This behavior is only the exceptional path.
        if (!EMERGENCY_FALLBACK_REQUESTED.contains(villager)) return false;
        if (villager.isSleeping()) return false;
        if (cooldown > 0) { cooldown--; return false; }

        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();

        RestDecision decision = RestCoordinator.decide(RestCoordinator.capture(villager, needs, false, false));
        RestCoordinator.recordDecision(villager, needs, decision, null);
        if (!decision.shouldSeekBed()) return false;

        emergencyClaimPos = null;

        // No usable assigned bed — find any nearby unclaimed bed.
        if (!VillagerSearchCadence.isDue(level, villager, SEARCH_CADENCE_KEY)) return false;
        BlockPos found = findNearbyUnclaimedBed(level, villager, level.getGameTime());
        if (found == null) {
            VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, 60, 20);
            RestCoordinator.recordBlockedDecision(villager, needs, decision.reason(), SleepBlockReason.NO_BED_FOUND, null);
            return false;
        }
        bedPos = found;
        emergencyClaimPos = found;
        RestCoordinator.recordDecision(villager, needs, decision, bedPos);
        return true;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (bedPos == null) return;
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        if (!TargetReachabilityCache.canAttempt(level, villager, bedPos)) {
            releaseEmergencyClaim(level, villager);
            RestCoordinator.recordBlockedDecision(villager, needs, SleepReason.fromId(needs.restDebugReasonId()), SleepBlockReason.BED_UNREACHABLE, bedPos);
            bedPos = null;
            cooldown = 40;
            VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, cooldown, 20);
            return;
        }
        // Verify reachability at start, not every tick in checkExtraStartConditions
        net.minecraft.world.level.pathfinder.Path path = villager.getNavigation().createPath(bedPos, CLOSE_ENOUGH);
        if (path == null || !path.canReach()) {
            TargetReachabilityCache.recordFailure(level, villager, bedPos, UNREACHABLE_BED_TTL_TICKS);
            releaseEmergencyClaim(level, villager);
            RestCoordinator.recordBlockedDecision(villager, needs, SleepReason.fromId(needs.restDebugReasonId()), SleepBlockReason.BED_UNREACHABLE, bedPos);
            bedPos = null;
            cooldown = 40;
            VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, cooldown, 20);
            doStop(level, villager, gameTime);
            return;
        }
        TargetReachabilityCache.clear(level, villager, bedPos);
        if (emergencyClaimPos != null) {
            EmergencyBedClaims.renew(level, villager.getUUID(), emergencyClaimPos, gameTime + EMERGENCY_CLAIM_TTL);
        }
        BehaviorUtils.setWalkAndLookTargetMemories(villager, bedPos, WALK_SPEED, CLOSE_ENOUGH);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (bedPos == null) {
            doStop(level, villager, gameTime);
            return;
        }

        if (emergencyClaimPos != null) {
            if (!EmergencyBedClaims.isClaimedBy(level, villager.getUUID(), emergencyClaimPos)) {
                releaseEmergencyClaim(level, villager);
                doStop(level, villager, gameTime);
                return;
            }
            EmergencyBedClaims.renew(level, villager.getUUID(), emergencyClaimPos, gameTime + EMERGENCY_CLAIM_TTL);
        }

        // Keep walking toward bed
        if (!villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent()) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, bedPos, WALK_SPEED, CLOSE_ENOUGH);
        }

        // Close enough to interact with Townstead emergency fallback bed
        double distSq = villager.distanceToSqr(bedPos.getX() + 0.5, bedPos.getY() + 0.5, bedPos.getZ() + 0.5);
        if (distSq <= BED_INTERACT_DIST_SQ) {
            BlockState state = level.getBlockState(bedPos);
            if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.OCCUPIED)) {
                // Bed is occupied or gone
                releaseEmergencyClaim(level, villager);
                doStop(level, villager, gameTime);
                return;
            }

            BlockPos headPos = normalizeBedHead(level, bedPos);
            if (headPos == null) {
                releaseEmergencyClaim(level, villager);
                doStop(level, villager, gameTime);
                return;
            }

            if (emergencyClaimPos != null && !headPos.equals(emergencyClaimPos)) {
                releaseEmergencyClaim(level, villager);
                doStop(level, villager, gameTime);
                return;
            }

            // Temporarily point HOME at the emergency bed so MCA's SleepInBed
            // behaviour handles occupancy, positioning, and wake-up normally.
            // The original HOME is saved in fatigue state and restored on wake.
            TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
            Optional<GlobalPos> prevHome = villager.getBrain().getMemory(MemoryModuleType.HOME);
            if (prevHome.isPresent()) {
                needs.saveHome(prevHome.get());
            } else {
                needs.saveNoHome();
            }
            needs.setEmergencyBed(headPos);
            villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), headPos));
            villager.getBrain().setActiveActivityIfPossible(Activity.REST);
            doStop(level, villager, gameTime);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!EMERGENCY_FALLBACK_REQUESTED.contains(villager)) return false;
        if (villager.isSleeping()) return false;
        if (bedPos == null) {
            releaseEmergencyClaim(level, villager);
            return false;
        }

        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        RestDecision decision = RestCoordinator.decide(RestCoordinator.capture(villager, needs, false, false));
        boolean keepUsing = decision.shouldSeekBed();
        if (!keepUsing) {
            releaseEmergencyClaim(level, villager);
        }
        return keepUsing;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!villager.isSleeping()) {
            releaseEmergencyClaim(level, villager);
        }
        bedPos = null;
        cooldown = 100;
        VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, cooldown, 20);
    }

    private static BlockPos findNearbyUnclaimedBed(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        // Use the POI system to find beds with space, then add a Townstead-local
        // temporary claim so fallback sleeping does not rewrite permanent HOME.
        var poiManager = level.getPoiManager();
        BlockPos center = villager.blockPosition();

        //? if >=1.21 {
        var bedType = net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME;
        java.util.Optional<BlockPos> found = poiManager.findClosest(
                holder -> holder.is(bedType),
                pos -> {
                    BlockPos headPos = normalizeBedHead(level, pos);
                    if (headPos == null) return false;
                    BlockState state = level.getBlockState(headPos);
                    return state.getBlock() instanceof BedBlock
                            && !state.getValue(BedBlock.OCCUPIED)
                            && !EmergencyBedClaims.isClaimedByOther(level, villager.getUUID(), headPos);
                },
                center, BED_SEARCH_RADIUS,
                net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE
        );
        //?} else {
        /*java.util.Optional<BlockPos> found = poiManager.findClosest(
                holder -> holder.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME),
                pos -> {
                    BlockPos headPos = normalizeBedHead(level, pos);
                    if (headPos == null) return false;
                    BlockState state = level.getBlockState(headPos);
                    return state.getBlock() instanceof BedBlock
                            && !state.getValue(BedBlock.OCCUPIED)
                            && !EmergencyBedClaims.isClaimedByOther(level, villager.getUUID(), headPos);
                },
                center, BED_SEARCH_RADIUS,
                net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE
        );
        *///?}

        if (found.isEmpty()) return null;

        BlockPos headPos = normalizeBedHead(level, found.get());
        if (headPos == null) return null;
        if (!TargetReachabilityCache.canAttempt(level, villager, headPos)) return null;
        if (!EmergencyBedClaims.tryClaim(
                level,
                villager.getUUID(),
                headPos,
                gameTime + EMERGENCY_CLAIM_TTL
        )) {
            return null;
        }
        return headPos;
    }

    private static BlockPos normalizeBedHead(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) return null;
        if (state.getValue(BedBlock.PART) == BedPart.FOOT) {
            return pos.relative(BedBlock.getConnectedDirection(state));
        }
        return pos.immutable();
    }

    private void releaseEmergencyClaim(ServerLevel level, VillagerEntityMCA villager) {
        if (emergencyClaimPos == null) return;
        EmergencyBedClaims.release(level, villager.getUUID(), emergencyClaimPos);
        emergencyClaimPos = null;
    }

    /** Called by the fatigue ticker after its single authoritative rest decision. */
    public static void requestEmergencyFallback(VillagerEntityMCA villager, boolean requested) {
        if (requested) EMERGENCY_FALLBACK_REQUESTED.add(villager);
        else EMERGENCY_FALLBACK_REQUESTED.remove(villager);
    }

    public static void forget(VillagerEntityMCA villager) {
        EMERGENCY_FALLBACK_REQUESTED.remove(villager);
    }
}
