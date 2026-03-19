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

    private BlockPos bedPos;
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

        // Find bed via HOME memory
        Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
        if (home.isEmpty()) return false;

        GlobalPos globalBed = home.get();
        if (globalBed.dimension() != level.dimension()) return false;

        BlockPos pos = globalBed.pos();
        // Don't walk too far (64 blocks)
        if (villager.blockPosition().distSqr(pos) > 64 * 64) return false;

        // Verify bed still exists
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) return false;
        //? if >=1.21 {
        if (state.getValue(BedBlock.OCCUPIED)) return false;
        //?} else {
        /*if (state.getValue(BedBlock.OCCUPIED)) return false;
        *///?}

        bedPos = pos;
        return true;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (bedPos == null) return;
        BehaviorUtils.setWalkAndLookTargetMemories(villager, bedPos, WALK_SPEED, CLOSE_ENOUGH);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (bedPos == null) {
            doStop(level, villager, gameTime);
            return;
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
                doStop(level, villager, gameTime);
                return;
            }
            // Find the head part of the bed for sleeping
            BlockPos headPos = bedPos;
            if (state.getValue(BedBlock.PART) == BedPart.FOOT) {
                headPos = bedPos.relative(BedBlock.getConnectedDirection(state));
            }
            // Set the brain's active activity to REST so vanilla's SleepInBed
            // behavior handles the actual sleeping mechanics
            villager.getBrain().setActiveActivityIfPossible(Activity.REST);
            // Also try startSleeping directly as a fallback
            if (!villager.isSleeping()) {
                villager.startSleeping(headPos);
            }
            doStop(level, villager, gameTime);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (villager.isSleeping()) return false;
        if (bedPos == null) return false;

        //? if neoforge {
        CompoundTag fatigue = villager.getData(Townstead.FATIGUE_DATA);
        //?} else {
        /*CompoundTag fatigue = villager.getPersistentData().getCompound("townstead_fatigue");
        *///?}
        // Stop seeking bed once rested enough (below drowsy threshold)
        return FatigueData.getFatigue(fatigue) >= FatigueData.DROWSY_THRESHOLD;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        bedPos = null;
        cooldown = 100;
    }
}
