package com.aetherianartificer.townstead.fatigue;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
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

/**
 * When a villager is fatigued (energy low), they will seek out their assigned bed
 * regardless of their current shift schedule. This fires before collapse.
 */
public class SeekBedWhenFatiguedTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 600;
    private static final float WALK_SPEED = 0.5f;
    private static final int CLOSE_ENOUGH = 1;
    private static final int BED_INTERACT_DIST_SQ = 9;
    private static final int BED_SEARCH_RADIUS = 48;
    private static final long EMERGENCY_CLAIM_TTL = MAX_DURATION + 200L;

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
        if (villager.isSleeping()) return false;
        if (cooldown > 0) { cooldown--; return false; }

        //? if neoforge {
        CompoundTag fatigue = villager.getData(Townstead.FATIGUE_DATA);
        //?} else {
        /*CompoundTag fatigue = villager.getPersistentData().getCompound("townstead_fatigue");
        *///?}

        // Seek bed when drowsy or worse (energy <= 8)
        if (FatigueData.getFatigue(fatigue) < FatigueData.DROWSY_THRESHOLD) return false;
        // Don't seek bed if already collapsed (they can't move)
        if (FatigueData.isCollapsed(fatigue)) return false;
        // Don't seek bed while fleeing from a mob — but DO allow it during
        // environmental panic (thirst damage, fire, etc.)
        if (villager.getLastHurtByMob() != null) return false;

        emergencyClaimPos = null;

        // Try assigned bed first (HOME memory)
        Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
        if (home.isPresent()) {
            GlobalPos globalBed = home.get();
            if (globalBed.dimension() == level.dimension()) {
                BlockPos pos = normalizeBedHead(level, globalBed.pos());
                if (pos != null && villager.blockPosition().distSqr(pos) <= 64 * 64) {
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof BedBlock) {
                        //? if >=1.21 {
                        if (!state.getValue(BedBlock.OCCUPIED)) { bedPos = pos; return true; }
                        //?} else {
                        /*if (!state.getValue(BedBlock.OCCUPIED)) { bedPos = pos; return true; }
                        *///?}
                    }
                }
            }
        }

        // No assigned bed or it's unavailable — find any nearby unclaimed bed
        BlockPos found = findNearbyUnclaimedBed(level, villager, level.getGameTime());
        if (found == null) return false;
        bedPos = found;
        emergencyClaimPos = found;
        return true;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (bedPos == null) return;
        // Verify reachability at start, not every tick in checkExtraStartConditions
        net.minecraft.world.level.pathfinder.Path path = villager.getNavigation().createPath(bedPos, CLOSE_ENOUGH);
        if (path == null || !path.canReach()) {
            releaseEmergencyClaim(level, villager);
            bedPos = null;
            doStop(level, villager, gameTime);
            return;
        }
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

        // Close enough to interact with bed
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

            // Set the brain's active activity to REST so vanilla's SleepInBed
            // behavior handles the actual sleeping mechanics when HOME exists.
            villager.getBrain().setActiveActivityIfPossible(Activity.REST);
            // Fallback sleeps directly for emergency beds without mutating HOME.
            if (!villager.isSleeping()) {
                villager.startSleeping(headPos);
            }
            doStop(level, villager, gameTime);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (villager.isSleeping()) return false;
        if (bedPos == null) {
            releaseEmergencyClaim(level, villager);
            return false;
        }

        //? if neoforge {
        CompoundTag fatigue = villager.getData(Townstead.FATIGUE_DATA);
        //?} else {
        /*CompoundTag fatigue = villager.getPersistentData().getCompound("townstead_fatigue");
        *///?}
        // Stop seeking bed once rested enough (below drowsy threshold)
        boolean keepUsing = FatigueData.getFatigue(fatigue) >= FatigueData.DROWSY_THRESHOLD;
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
}
