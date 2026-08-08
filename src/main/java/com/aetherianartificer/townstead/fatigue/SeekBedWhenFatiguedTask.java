package com.aetherianartificer.townstead.fatigue;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Last-resort temporary bed borrowing after MCA has had time to acquire HOME.
 * The POI ticket is real and its ownership is persisted; MCA owns navigation
 * and sleeping after the temporary HOME is installed.
 */
public final class SeekBedWhenFatiguedTask extends Behavior<VillagerEntityMCA> {
    private static final int SEARCH_RADIUS = 48;
    private static final Set<VillagerEntityMCA> REQUESTED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private BlockPos candidate;

    public SeekBedWhenFatiguedTask() {
        super(ImmutableMap.of(
                MemoryModuleType.HOME, MemoryStatus.REGISTERED,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), 20);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!TownsteadConfig.isVillagerFatigueEnabled() || villager.isSleeping()) return false;
        // MCA's HOME finder runs in CORE at priority 10. This fallback runs at
        // priority 65, so HOME is the handoff signal within the same brain tick.
        if (!REQUESTED.contains(villager)) return false;
        if (villager.getBrain().getMemory(MemoryModuleType.HOME).isPresent()) return false;

        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        long dayTime = villager.level().getDayTime() % 24000L;
        Activity scheduledActivity = villager.getBrain().getSchedule().getActivityAt((int) dayTime);
        RestDecision decision = RestCoordinator.decide(RestCoordinator.capture(
                villager,
                needs,
                true,
                false,
                scheduledActivity,
                needs.restOverrideActive()
        ));
        if (!decision.shouldSeekBed() || needs.hasEmergencyBed()) return false;

        candidate = level.getPoiManager().findClosest(
                holder -> holder.is(PoiTypes.HOME),
                pos -> isBorrowableBed(level.getBlockState(pos)),
                villager.blockPosition(), SEARCH_RADIUS, PoiManager.Occupancy.HAS_SPACE
        ).orElse(null);
        return candidate != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (candidate == null || villager.getBrain().getMemory(MemoryModuleType.HOME).isPresent()) {
            candidate = null;
            return;
        }

        // Atomically acquire the exact POI selected above. If MCA or another
        // villager won the race, take() fails and Townstead changes nothing.
        Optional<BlockPos> claimed = level.getPoiManager().take(
                holder -> holder.is(PoiTypes.HOME),
                (holder, pos) -> pos.equals(candidate) && isBorrowableBed(level.getBlockState(pos)),
                candidate, 1
        );
        if (claimed.isEmpty() || villager.getBrain().getMemory(MemoryModuleType.HOME).isPresent()) {
            claimed.ifPresent(level.getPoiManager()::release);
            candidate = null;
            return;
        }

        BlockPos bed = claimed.get().immutable();
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        needs.saveNoHome();
        GlobalPos temporaryHome = GlobalPos.of(level.dimension(), bed);
        needs.setEmergencyBed(temporaryHome, true);
        villager.getBrain().setMemory(MemoryModuleType.HOME, temporaryHome);
        villager.getBrain().setActiveActivityIfPossible(Activity.REST);
        TownsteadVillagers.flush(villager);
        candidate = null;
    }

    private static boolean isBorrowableBed(BlockState state) {
        return state.is(BlockTags.BEDS)
                && state.hasProperty(BedBlock.OCCUPIED)
                && !state.getValue(BedBlock.OCCUPIED);
    }

    public static void requestEmergencyFallback(VillagerEntityMCA villager, boolean requested) {
        if (!requested) {
            REQUESTED.remove(villager);
        } else {
            REQUESTED.add(villager);
        }
    }

    public static void forget(VillagerEntityMCA villager) {
        REQUESTED.remove(villager);
    }
}
