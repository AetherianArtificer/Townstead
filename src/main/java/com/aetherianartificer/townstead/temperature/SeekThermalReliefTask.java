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

/**
 * Takes a relief break for Cold/Hot or worse, including during work, and yields once
 * the body is comfortable or mildly affected and safely recovering. Freezing water is left at once.
 */
public class SeekThermalReliefTask extends Behavior<VillagerEntityMCA> {
    private static final String SEARCH_CADENCE_KEY = "thermal_relief";
    private static final float WALK_SPEED = 0.6f;
    private static final int CLOSE_ENOUGH = 0;
    // Recovering from a cold or hot body takes minutes; the break must allow it.
    private static final int MAX_DURATION = 9600;
    private static final float FREEZING_WATER_LOAD = -12f;
    private static final int REFRACTORY_TICKS = 600;
    private static final int SEARCH_RETRY_TICKS = 600;
    private static final int REASSERT_TICKS = 40;

    private ThermalReliefTargets.Target target;
    private ThermalProfile profile = ThermalProfile.DEFAULT;
    private boolean arrived;
    private boolean seekingCold;
    private long lastReassert;
    private long lastReview, progressAt;
    private float progressBody;
    private final java.util.Set<BlockPos> failed = new java.util.HashSet<>();
    private double closestDistance;
    private long walkingProgressAt;

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
        if (!enabled() || ThermalCare.interrupted(villager) || villager.isBaby()
                || !ThermalCare.available(villager, "relief")) return false;
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        if (!needs.hasBodyTemp()) return false;
        TemperatureData.Tier core = TemperatureData.Tier.values()[needs.coreThermalTier()];
        boolean freezingWater = villager.isInWater() && needs.comfortLoad() <= FREEZING_WATER_LOAD;
        if (core == TemperatureData.Tier.COMFORTABLE && !freezingWater) return false;
        if (villager.getLastHurtByMob() != null || villager.getVillagerBrain().isPanicking()) return false;
        if (!VillagerSearchCadence.isDue(level, villager, SEARCH_CADENCE_KEY)) return false;
        // Gene lookups only once the cheap checks pass and the search cadence is due.
        if (!ThermalExposure.enabled(villager)) return false;
        profile = ThermalProfile.of(villager);
        if (!freezingWater && !ThermalCare.needsBreak(villager,
                ThermalExposure.at(level, villager, villager.blockPosition(), ThermalExposure.activity(villager)))) return false;

        seekingCold = core == TemperatureData.Tier.COMFORTABLE ? freezingWater : core.isCold();
        failed.clear();
        target = ThermalReliefTargets.find(level, villager, seekingCold, profile);
        if (target == null) {
            VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, SEARCH_RETRY_TICKS, 200);
            needs.setReliefDebug("no_effective_relief");
            return false;
        }
        return com.aetherianartificer.townstead.hunger.ConsumableTargetClaims.tryClaimPos(level, villager.getUUID(),
                ThermalReliefTargets.CLAIM, target.pos(), level.getGameTime() + 200);
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        arrived = false;
        closestDistance = Double.MAX_VALUE;
        walkingProgressAt = gameTime;
        lastReview = progressAt = gameTime;
        progressBody = Math.abs(BodyHeat.deviation(TownsteadVillagers.get(villager).needs().bodyTempTenths(), profile));
        ThermalCare.hold(villager, "relief");
        lastReassert = gameTime - REASSERT_TICKS;
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        needs.setSeekingRelief(true);
        needs.setReliefDebug(target.description());
        walk(villager);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target == null) return;
        ThermalCare.hold(villager, "relief");
        com.aetherianartificer.townstead.hunger.ConsumableTargetClaims.tryClaimPos(level, villager.getUUID(),
                ThermalReliefTargets.CLAIM, target.pos(), gameTime + 200);
        if (gameTime - lastReview >= 100) {
            lastReview = gameTime;
            float deviation = Math.abs(BodyHeat.deviation(TownsteadVillagers.get(villager).needs().bodyTempTenths(), profile));
            if (deviation < progressBody - .04f) { progressBody = deviation; progressAt = gameTime; }
            boolean stalled = arrived && gameTime - progressAt > 900 || !arrived && gameTime - walkingProgressAt > 600;
            boolean invalid = !"dry_land".equals(target.description()) && !"safer_refuge".equals(target.description())
                    && !ThermalReliefTargets.useful(level, villager, target.pos());
            if (stalled || invalid) {
                failed.add(target.pos());
                replan(level, villager, gameTime);
                if (target == null) return;
            }
        }
        BlockPos pos = target.pos();
        double distSq = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        boolean atTarget = villager.blockPosition().equals(pos) && !villager.isInWater();
        if (distSq < closestDistance - 1) { closestDistance = distSq; walkingProgressAt = gameTime; }
        if (!arrived && atTarget) {
            arrived = true;
            progressAt = gameTime;
            villager.getNavigation().stop();
            if ("dry_land".equals(target.description())) {
                replan(level, villager, gameTime);
                return;
            }
        }
        // Ordinary idle/work behaviors can replace WALK_TARGET every tick. Hold the actual
        // evaluated tile while recovering instead of allowing a two-block wander out of its heat.
        if (arrived && atTarget) {
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getNavigation().stop();
        } else walk(villager);
        if (gameTime - lastReassert >= REASSERT_TICKS) {
            lastReassert = gameTime;
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,
                    new net.minecraft.world.entity.ai.behavior.BlockPosTracker(pos));
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!"relief".equals(ThermalCare.owner(villager))) return false;
        if (!enabled() || target == null || ThermalCare.interrupted(villager) || !level.isLoaded(target.pos())
                || !level.getFluidState(target.pos()).isEmpty()) return false;
        if (villager.getLastHurtByMob() != null || villager.getVillagerBrain().isPanicking()) return false;
        if (!ThermalExposure.enabled(villager)) return false;
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        if (villager.isInWater() && seekingCold) return true;
        return !recoveryComplete(needs);
    }

    private boolean recoveryComplete(TownsteadVillager.Needs needs) {
        return BodyHeat.reliefComplete(TemperatureData.celsius(needs.bodyTempTenths()),
                BodyHeat.target(needs.comfortLoad(), TemperatureSettings.get().comfortZone(), profile, false), profile);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        boolean ownsWalk = "relief".equals(ThermalCare.owner(villager));
        ThermalCare.release(villager, "relief");
        if (target != null) com.aetherianartificer.townstead.hunger.ConsumableTargetClaims.releasePos(level,
                villager.getUUID(), ThermalReliefTargets.CLAIM, target.pos());
        if (recoveryComplete(needs)) needs.setReliefDebug("recovered");
        target = null;
        arrived = false;
        if (ownsWalk) villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, REFRACTORY_TICKS, 40);
    }

    private void walk(VillagerEntityMCA villager) {
        BehaviorUtils.setWalkAndLookTargetMemories(villager, target.pos(), WALK_SPEED, CLOSE_ENOUGH);
    }

    private void replan(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target != null) com.aetherianartificer.townstead.hunger.ConsumableTargetClaims.releasePos(level,
                villager.getUUID(), ThermalReliefTargets.CLAIM, target.pos());
        target = ThermalReliefTargets.find(level, villager, seekingCold, profile, failed);
        arrived = false;
        closestDistance = Double.MAX_VALUE;
        walkingProgressAt = gameTime;
        progressAt = gameTime;
        progressBody = Math.abs(BodyHeat.deviation(TownsteadVillagers.get(villager).needs().bodyTempTenths(), profile));
        if (target != null && !com.aetherianartificer.townstead.hunger.ConsumableTargetClaims.tryClaimPos(level,
                villager.getUUID(), ThermalReliefTargets.CLAIM, target.pos(), gameTime + 200)) target = null;
        TownsteadVillagers.get(villager).needs().setReliefDebug(target == null ? "no_effective_relief" : target.description());
        if (target != null) walk(villager);
    }

}
