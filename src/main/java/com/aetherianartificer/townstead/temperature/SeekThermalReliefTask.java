package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.hunger.VillagerSearchCadence;
import com.aetherianartificer.townstead.root.needs.NeedSuppression;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

/** Takes a relief break for accumulated discomfort or a core crisis, including during work. */
public class SeekThermalReliefTask extends Behavior<VillagerEntityMCA> {
    private static final String SEARCH_CADENCE_KEY = "thermal_relief";
    private static final float WALK_SPEED = 0.6f;
    private static final int CLOSE_ENOUGH = 0;
    private static final int MAX_DURATION = 2400;
    private static final int REFRACTORY_TICKS = 600;
    private static final int SEARCH_RETRY_TICKS = 600;
    private static final int REASSERT_TICKS = 40;

    private ThermalReliefTargets.Target target;
    private ThermalProfile profile = ThermalProfile.DEFAULT;
    private boolean arrived;
    private boolean seekingCold;
    private long lastReassert;

    public SeekThermalReliefTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    private static boolean enabled() {
        return TownsteadConfig.isVillagerTemperatureEnabled();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!enabled() || villager.isSleeping() || villager.isBaby()) return false;
        // Comfort drives early breaks; core crisis can always trigger one.
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        if (!needs.hasBodyTemp()) return false;
        TemperatureData.Tier tier = TemperatureData.Tier.values()[Math.min(6, Math.max(0, needs.thermalTier()))];
        TemperatureData.Tier core = TemperatureData.Tier.values()[needs.coreThermalTier()];
        if (!core.isCrisis() && !ThermalComfort.needsBreak(needs.comfortLoad(),
                needs.thermalStrainSeconds(), TemperatureSettings.get().comfortBreakSeconds(), villager.isInWater())) return false;
        if (villager.getLastHurtByMob() != null || villager.getVillagerBrain().isPanicking()) return false;
        if (!VillagerSearchCadence.isDue(level, villager, SEARCH_CADENCE_KEY)) return false;
        // Gene lookups only once the cheap checks pass and the search cadence is due.
        if (NeedSuppression.suppressesTemperature(villager)) return false;
        profile = ThermalProfile.of(villager);

        seekingCold = core.isCrisis() ? core.isCold() : tier.isCold();
        target = ThermalReliefTargets.find(level, villager, seekingCold, profile.ectotherm());
        if (target == null) {
            VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, SEARCH_RETRY_TICKS, 200);
            needs.setReliefDebug("none_found");
            return false;
        }
        return true;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        arrived = false;
        lastReassert = gameTime - REASSERT_TICKS;
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        needs.setSeekingRelief(true);
        needs.setReliefDebug(target.description());
        walk(villager);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target == null) return;
        BlockPos pos = target.pos();
        double distSq = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (!arrived && !villager.isInWater() && distSq <= (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) {
            arrived = true;
            villager.getNavigation().stop();
            if ("dry_land".equals(target.description())) {
                ThermalReliefTargets.Target next = ThermalReliefTargets.find(level, villager, true, profile.ectotherm());
                if (next != null && !next.pos().equals(pos)) {
                    target = next;
                    arrived = false;
                    TownsteadVillagers.get(villager).needs().setReliefDebug(next.description());
                    walk(villager);
                    return;
                }
            }
        }
        if (gameTime - lastReassert >= REASSERT_TICKS) {
            lastReassert = gameTime;
            if (!arrived || distSq > (CLOSE_ENOUGH + 2) * (CLOSE_ENOUGH + 2)) walk(villager);
            else villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,
                    new net.minecraft.world.entity.ai.behavior.BlockPosTracker(pos));
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!enabled() || target == null || villager.isSleeping() || !level.isLoaded(target.pos())
                || !level.getFluidState(target.pos()).isEmpty()) return false;
        if (villager.getLastHurtByMob() != null || villager.getVillagerBrain().isPanicking()) return false;
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        if (seekingCold ? needs.comfortLoad() >= 6 : needs.comfortLoad() <= -6) return false;
        return !NeedSuppression.suppressesTemperature(villager)
                && (!ThermalComfort.recovered(needs.comfortLoad(), needs.thermalStrainSeconds(),
                        TemperatureSettings.get().comfortBreakSeconds()) || !profile.comfortable(needs.bodyTempTenths()));
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        needs.setSeekingRelief(false);
        needs.setReliefDebug("none");
        target = null;
        arrived = false;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, REFRACTORY_TICKS, 40);
    }

    private void walk(VillagerEntityMCA villager) {
        BehaviorUtils.setWalkAndLookTargetMemories(villager, target.pos(), WALK_SPEED, CLOSE_ENOUGH);
    }

}
