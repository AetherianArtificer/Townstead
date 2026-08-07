package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.aetherianartificer.townstead.fatigue.EmergencyBedClaims;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.fatigue.RestCoordinator;
import com.aetherianartificer.townstead.fatigue.RestDebugData;
import com.aetherianartificer.townstead.fatigue.RestDecision;
import com.aetherianartificer.townstead.fatigue.SleepReason;
import com.aetherianartificer.townstead.fatigue.SeekBedWhenFatiguedTask;
import com.aetherianartificer.townstead.hunger.TargetReachabilityCache;
import com.aetherianartificer.townstead.root.chronotype.Chronotypes;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FatigueVillagerTicker {
    //? if >=1.21 {
    private static final ResourceLocation TOWNSTEAD_FATIGUE_SPEED =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "fatigue_speed_penalty");
    //?} else {
    /*private static final ResourceLocation TOWNSTEAD_FATIGUE_SPEED =
            new ResourceLocation(Townstead.MOD_ID, "fatigue_speed_penalty");
    *///?}
    //? if forge {
    /*private static final java.util.UUID TOWNSTEAD_FATIGUE_SPEED_UUID =
            java.util.UUID.nameUUIDFromBytes("townstead:fatigue_speed_penalty".getBytes());
    *///?}

    private static final Map<Integer, TickState> STATE = new ConcurrentHashMap<>();

    private FatigueVillagerTicker() {}

    /**
     * Hold a fatigue-forced sleeper in REST before MCA runs its brain tick.
     * The normal dispatcher runs at aiStep TAIL, which is too late when a
     * previously active WORK behavior has already caused SleepInBed to stop.
     */
    public static void preAiStep(VillagerEntityMCA self) {
        if (!(self.level() instanceof ServerLevel)) return;
        if (!TownsteadConfig.isVillagerFatigueEnabled() || !self.isSleeping()) return;

        TownsteadVillager.Needs needs = TownsteadVillagers.get(self).needs();
        if (!needs.restOverrideActive() || needs.fatigue() <= 0) return;

        if (currentScheduleActivity(self) != Activity.REST) {
            com.aetherianartificer.townstead.shift.ShiftScheduleApplier.overrideToRest(self);
        }
        if (!self.getBrain().isActive(Activity.REST)) {
            self.getBrain().setActiveActivityIfPossible(Activity.REST);
        }
    }

    public static void tick(VillagerEntityMCA self) {
        if (!(self.level() instanceof ServerLevel level)) return;
        if (!TownsteadConfig.isVillagerFatigueEnabled() || self.isBaby()) {
            SeekBedWhenFatiguedTask.forget(self);
            return;
        }

        TownsteadVillager.Needs needs = TownsteadVillagers.get(self).needs();

        TickState state = STATE.computeIfAbsent(self.getId(), id -> new TickState());
        int oldFatigue = needs.fatigue();
        boolean changed = false;

        // --- Collapse enforcement (every tick) ---
        if (needs.collapsed()) {
            // Collapsed villagers cannot move — erase movement memories
            self.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            self.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            self.getNavigation().stop();
            // Spawn exhaustion particles every 10 ticks
            if (self.tickCount % 10 == 0) {
                level.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.SMOKE,
                        self.getX(), self.getEyeY() + 0.3, self.getZ(),
                        2, 0.15, 0.1, 0.15, 0.01);
            }
        }

        // --- Accumulation / recovery on interval (dayTime-based) ---
        long dayTime = level.getDayTime();
        if (state.lastFatigueDayTime < 0) state.lastFatigueDayTime = dayTime;

        Chronotypes.Resolved chronotype = Chronotypes.resolve(self);
        boolean isNocturnal = chronotype.isNocturnal();
        boolean inBed = self.isSleeping();
        Activity activity = currentScheduleActivity(self);
        boolean inCombat = self.getVillagerBrain().isPanicking()
                || self.getLastHurtByMob() != null;
        long timeOfDay = dayTime % 24000L;
        boolean isCycleAligned = isCycleAligned(isNocturnal, timeOfDay);
        // Precise chronotype sleep window governs BED recovery: sleeping inside
        // the villager's assigned hours recovers fully, off-window naps recover
        // slowly. tick-hour 0 == 6 AM == dayTime 0, matching the sleep window + the UI.
        int tickHour = (int) (timeOfDay / 1000L);
        boolean inSleepWindow = chronotype.isPreferredSleepHour(tickHour);

        int fatigueIterations = 0;
        while (dayTime - state.lastFatigueDayTime >= FatigueData.ACCUMULATION_INTERVAL && fatigueIterations < 100) {
            state.lastFatigueDayTime += FatigueData.ACCUMULATION_INTERVAL;
            fatigueIterations++;

            if (needs.collapsed()) {
                // Collapsed recovery runs on gameTime below so it's robust to
                // doDaylightCycle=false and time-scaling mods. Skip here.
                continue;
            } else if (inBed) {
                // Sleeping inside the chronotype window = full recovery; an
                // off-window nap recovers at the reduced rate.
                float recovery = inSleepWindow
                        ? FatigueData.RECOVERY_BED_ALIGNED
                        : FatigueData.RECOVERY_BED_MISALIGNED;
                applyFatigueDelta(needs, state, recovery);
            } else if (activity == Activity.REST && !needs.restOverrideActive()) {
                applyFatigueDelta(needs, state, FatigueData.RECOVERY_REST_NO_BED);
            } else {
                float rate;
                if (activity == Activity.WORK) {
                    rate = FatigueData.RATE_WORK;
                } else if (activity == Activity.MEET) {
                    rate = FatigueData.RATE_MEET;
                } else {
                    rate = FatigueData.RATE_IDLE;
                }
                if (inCombat) {
                    rate *= FatigueData.COMBAT_MULTIPLIER;
                }
                float alignedMult = TownsteadConfig.FATIGUE_NOCTURNAL_MULTIPLIER.get().floatValue();
                float misalignedMult = TownsteadConfig.FATIGUE_MISALIGNED_MULTIPLIER.get().floatValue();
                if (isCycleAligned) {
                    rate *= alignedMult;
                } else {
                    rate *= misalignedMult;
                }
                applyFatigueDelta(needs, state, rate);
            }
        }

        // --- Collapsed recovery on gameTime (independent of dayTime) ---
        long gameTime = level.getGameTime();
        int collapsedIterations = 0;
        if (needs.collapsed()) {
            if (state.lastCollapsedGameTime < 0) state.lastCollapsedGameTime = gameTime;
            while (gameTime - state.lastCollapsedGameTime >= FatigueData.COLLAPSED_GAMETIME_INTERVAL
                    && collapsedIterations < 100) {
                state.lastCollapsedGameTime += FatigueData.COLLAPSED_GAMETIME_INTERVAL;
                collapsedIterations++;
                applyFatigueDelta(needs, state, FatigueData.RECOVERY_COLLAPSED);
                FatigueData.tryAutoDrinkCoffee(self);
                if (!needs.collapsed()) break;
            }
        } else {
            state.lastCollapsedGameTime = gameTime;
        }

        if (fatigueIterations > 0 || collapsedIterations > 0) {
            changed = needs.fatigue() != oldFatigue;
        }

        // --- Collapse / gate / auto-coffee (runs after interval processing) ---
        if (changed || fatigueIterations > 0 || collapsedIterations > 0) {
            int currentFatigue = needs.fatigue();
            int collapseThreshold = FatigueData.COLLAPSE_THRESHOLD;
            int recoveryGate = FatigueData.RECOVERY_GATE;

            // --- Safety: clear stale collapse if fatigue is below threshold ---
            if (needs.collapsed() && currentFatigue < collapseThreshold) {
                needs.setCollapsed(false);
                needs.setGated(false);
                changed = true;
                if (TownsteadConfig.ENABLE_FATIGUE_ALERTS.get()) {
                    self.sendChatToAllAround("dialogue.chat.energy.recovered/"
                            + (1 + level.random.nextInt(4)));
                }
            }

            // --- Collapse check ---
            if (currentFatigue >= collapseThreshold && !self.isSleeping() && !needs.collapsed()) {
                needs.setCollapsed(true);
                needs.setGated(true);
                changed = true;
                if (TownsteadConfig.ENABLE_FATIGUE_ALERTS.get()) {
                    self.sendChatToAllAround("dialogue.chat.energy.collapsed/"
                            + (1 + level.random.nextInt(4)));
                }
            }

            // --- Gate release check ---
            if (currentFatigue < recoveryGate && needs.gated()) {
                boolean wasCollapsedHere = needs.collapsed();
                needs.setGated(false);
                needs.setCollapsed(false);
                changed = true;
                if (wasCollapsedHere && TownsteadConfig.ENABLE_FATIGUE_ALERTS.get()) {
                    self.sendChatToAllAround("dialogue.chat.energy.recovered/"
                            + (1 + level.random.nextInt(4)));
                }
            }

            // --- Auto-drink coffee when drowsy or worse ---
            currentFatigue = needs.fatigue();
            if (currentFatigue >= FatigueData.DROWSY_THRESHOLD) {
                if (FatigueData.tryAutoDrinkCoffee(self)) {
                    changed = true;
                }
            }
        }

        // --- Fatigue schedule override (before rest decision so wake check sees correct schedule) ---
        boolean overrideActive = needs.restOverrideActive();
        if (overrideActive && state.preOverrideSchedule == null) {
            // The flag is persisted with the villager, whereas TickState and the
            // brain's schedule are rebuilt after a server restart/entity reload.
            // Reconstruct the runtime half of the override before evaluating it.
            // apply() restores a configured Townstead shift when one exists and
            // otherwise leaves MCA's freshly initialized schedule alone.
            com.aetherianartificer.townstead.shift.ShiftScheduleApplier.apply(self);
            state.preOverrideSchedule = self.getBrain().getSchedule();
            com.aetherianartificer.townstead.shift.ShiftScheduleApplier.overrideToRest(self);
        }
        Activity naturalScheduleActivity = currentScheduleActivity(self, overrideActive ? state.preOverrideSchedule : null);
        RestDecision naturalRestDecision = RestCoordinator.decide(
                RestCoordinator.capture(self, needs, hasValidSleepingBed(self), false, naturalScheduleActivity, false)
        );
        if (naturalRestDecision.shouldOverrideScheduleToRest()) {
            if (!overrideActive) {
                state.preOverrideSchedule = self.getBrain().getSchedule();
                com.aetherianartificer.townstead.shift.ShiftScheduleApplier.overrideToRest(self);
                needs.setRestOverride(true, SleepReason.FATIGUE_REST);
                // The remainder of this tick must evaluate against the natural
                // pre-override schedule. Otherwise the freshly installed all-REST
                // schedule is mistaken for ordinary scheduled sleep and suppresses
                // the fatigue bed request until the villager stands idle forever.
                overrideActive = true;
            }
        } else if (overrideActive && !self.isSleeping()) {
            // Restore the pre-override schedule first, then let apply() overwrite
            // if the villager has custom shifts. This prevents the schedule from
            // staying stuck on all-REST for villagers without custom shifts,
            // since apply() is a no-op for them.
            if (state.preOverrideSchedule != null) {
                self.getBrain().setSchedule(state.preOverrideSchedule);
                state.preOverrideSchedule = null;
            }
            com.aetherianartificer.townstead.shift.ShiftScheduleApplier.apply(self);
            needs.setRestOverride(false, SleepReason.NONE);
            overrideActive = false;
        }

        // --- Rest decisions (after schedule restore so wake check sees correct schedule) ---
        Activity decisionScheduleActivity = overrideActive && state.preOverrideSchedule != null
                ? currentScheduleActivity(self, state.preOverrideSchedule)
                : currentScheduleActivity(self);
        RestDecision restDecision = RestCoordinator.decide(
                RestCoordinator.capture(self, needs, hasValidSleepingBed(self), false, decisionScheduleActivity, overrideActive)
        );
        // Townstead decides *why* rest is needed; MCA owns ordinary HOME
        // navigation and SleepInBed. Only arm the custom brain behavior when
        // there is no usable assigned bed and an emergency fallback is required.
        boolean shouldSeek = restDecision.shouldSeekBed();
        BlockPos assignedBed = shouldSeek ? usableAssignedBed(level, self) : null;
        RestCoordinator.recordDecision(self, needs, restDecision, assignedBed);
        boolean needsEmergencyFallback = shouldSeek && assignedBed == null;
        SeekBedWhenFatiguedTask.requestEmergencyFallback(self, needsEmergencyFallback);
        if (assignedBed != null) {
            // MCA's REST package owns HOME navigation and SleepInBed, but its
            // UpdateActivityFromSchedule behavior may replace the active
            // activity. Reassert only when it has actually been dropped.
            if (!self.getBrain().isActive(Activity.REST)) {
                self.getBrain().setActiveActivityIfPossible(Activity.REST);
            }
        }

        // Sleeping villagers should not keep executing stale movement orders.
        if (self.isSleeping() && !needs.collapsed()) {
            self.getSleepingPos().ifPresent(pos ->
                    EmergencyBedClaims.renew(level, self.getUUID(), pos, level.getGameTime() + 200L));
            if (restDecision.shouldWake()) {
                EmergencyBedClaims.releaseAll(level, self.getUUID());
                self.stopSleeping();
                restoreHomeAfterEmergencySleep(self, needs);
            }
            self.getNavigation().stop();
            self.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            self.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            self.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }

        // If the villager is NOT sleeping but has a persisted emergency bed,
        // the sleep was interrupted (death, chunk unload, etc.). Restore the
        // original HOME memory so MCA's village assignment isn't corrupted.
        if (!self.isSleeping() && needs.hasEmergencyBed()) {
            restoreHomeAfterEmergencySleep(self, needs);
        }

        // --- Mood drift (dayTime-based) ---
        if (state.lastMoodDayTime < 0) state.lastMoodDayTime = dayTime;
        if (dayTime - state.lastMoodDayTime >= FatigueData.MOOD_CHECK_INTERVAL) {
            state.lastMoodDayTime = dayTime;
            int f = needs.fatigue();
            FatigueData.FatigueState fatigueState = FatigueData.getState(f);
            float pressure = FatigueData.getMoodPressure(fatigueState);
            float drift = needs.fatigueMoodDrift() + pressure;
            int moodDelta = 0;
            if (drift >= 1f) moodDelta = (int) Math.floor(drift);
            else if (drift <= -1f) moodDelta = (int) Math.ceil(drift);

            if (moodDelta != 0) {
                self.getVillagerBrain().modifyMoodValue(moodDelta);
                drift -= moodDelta;
            }
            needs.setFatigueMoodDrift(drift);
        }

        // --- Speed modifier ---
        updateSpeedModifier(self, needs.fatigue(), state);

        // --- Sync ---
        int currentFatigue = needs.fatigue();
        boolean currentCollapsed = needs.collapsed();
        if (currentFatigue != state.lastSyncedFatigue || currentCollapsed != state.lastSyncedCollapsed) {
            state.lastSyncedFatigue = currentFatigue;
            state.lastSyncedCollapsed = currentCollapsed;
            CompoundTag fatigue = needs.fatigueTag();
            //? if neoforge {
            PacketDistributor.sendToPlayersTrackingEntity(self, Townstead.townstead$fatigueSync(self, fatigue));
            //?} else if forge {
            /*TownsteadNetwork.sendToTrackingEntity(self, Townstead.townstead$fatigueSync(self, fatigue));
            *///?}
        }

        // --- Cleanup ---
        if (!self.isAlive() || self.isRemoved()) {
            restoreHomeAfterEmergencySleep(self, needs);
            EmergencyBedClaims.releaseAll(level, self.getUUID());
            SeekBedWhenFatiguedTask.forget(self);
            STATE.remove(self.getId());
        }
    }

    /**
     * Check if current time is aligned with the villager's natural cycle.
     * Diurnal: work 7AM-6PM (ticks 1000-11999), sleep 7PM-6AM (ticks 13000-23999+0-999)
     * Nocturnal: inverted
     */
    private static boolean isCycleAligned(boolean isNocturnal, long dayTime) {
        // Daytime work hours: ticks 1000-11999
        boolean isDaytimeHours = dayTime >= 1000 && dayTime < 12000;
        // Diurnal villagers are aligned during daytime work hours
        // Nocturnal villagers are aligned during nighttime work hours
        return isNocturnal != isDaytimeHours;
    }

    /**
     * Applies a float delta to fatigue using residual accumulation.
     * Small deltas (e.g. -0.15) accumulate across intervals until they
     * cross a whole-point threshold, preventing rounding to zero.
     */
    private static void applyFatigueDelta(TownsteadVillager.Needs needs, TickState state, float delta) {
        state.fatigueResidue += delta;
        int wholeDelta;
        if (state.fatigueResidue >= 1f) {
            wholeDelta = (int) Math.floor(state.fatigueResidue);
        } else if (state.fatigueResidue <= -1f) {
            wholeDelta = (int) Math.ceil(state.fatigueResidue);
        } else {
            return;
        }
        state.fatigueResidue -= wholeDelta;
        needs.addFatigue(wholeDelta);
    }

    private static Activity currentScheduleActivity(VillagerEntityMCA self) {
        return currentScheduleActivity(self, null);
    }

    private static Activity currentScheduleActivity(VillagerEntityMCA self, Schedule scheduleOverride) {
        long dayTime = self.level().getDayTime() % 24000L;
        Schedule schedule = scheduleOverride != null ? scheduleOverride : self.getBrain().getSchedule();
        return schedule.getActivityAt((int) dayTime);
    }

    private static boolean hasValidSleepingBed(VillagerEntityMCA self) {
        return self.getSleepingPos()
                .map(BlockPos::immutable)
                .filter(pos -> self.level().isLoaded(pos))
                .map(self.level()::getBlockState)
                .map(FatigueVillagerTicker::isBedBlock)
                .orElse(false);
    }

    private static boolean isBedBlock(BlockState state) {
        return state.getBlock() instanceof BedBlock;
    }

    private static BlockPos usableAssignedBed(ServerLevel level, VillagerEntityMCA self) {
        var home = self.getBrain().getMemory(MemoryModuleType.HOME);
        if (home.isEmpty() || home.get().dimension() != level.dimension()) return null;
        BlockPos pos = normalizeBedHead(level, home.get().pos());
        if (pos == null || self.blockPosition().distSqr(pos) > 64 * 64) return null;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.OCCUPIED)) return null;
        return TargetReachabilityCache.canAttempt(level, self, pos) ? pos : null;
    }

    private static BlockPos normalizeBedHead(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) return null;
        if (state.getValue(BedBlock.PART)
                == net.minecraft.world.level.block.state.properties.BedPart.FOOT) {
            return pos.relative(BedBlock.getConnectedDirection(state));
        }
        return pos.immutable();
    }

    /**
     * Restores the villager's original HOME memory after an emergency bed
     * sleep and clears the emergency bed tracking from fatigue NBT.
     * MCA owns bed occupancy — stopSleeping() already handles clearing
     * BedBlock.OCCUPIED, so we only need to fix the HOME pointer.
     */
    private static void restoreHomeAfterEmergencySleep(VillagerEntityMCA self, TownsteadVillager.Needs needs) {
        if (!needs.hasEmergencyBed()) return;
        if (needs.hasSavedHome()) {
            if (needs.wasPreviouslyHomeless()) {
                self.getBrain().eraseMemory(MemoryModuleType.HOME);
            } else {
                net.minecraft.core.GlobalPos savedHome = needs.savedHome();
                if (savedHome != null) {
                    self.getBrain().setMemory(MemoryModuleType.HOME, savedHome);
                }
            }
            needs.clearSavedHome();
        }
        needs.clearEmergencyBed();
    }

    private static void updateSpeedModifier(VillagerEntityMCA self, int currentFatigue, TickState state) {
        double penalty = FatigueData.getSpeedPenalty(currentFatigue);
        if (penalty == state.lastPenalty) return;
        state.lastPenalty = penalty;

        AttributeInstance speedAttr = self.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;

        //? if >=1.21 {
        AttributeModifier existing = speedAttr.getModifier(TOWNSTEAD_FATIGUE_SPEED);
        if (existing != null) speedAttr.removeModifier(TOWNSTEAD_FATIGUE_SPEED);
        if (penalty != 0.0) {
            speedAttr.addTransientModifier(new AttributeModifier(
                    TOWNSTEAD_FATIGUE_SPEED,
                    penalty,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }
        //?} else {
        /*AttributeModifier existing = speedAttr.getModifier(TOWNSTEAD_FATIGUE_SPEED_UUID);
        if (existing != null) speedAttr.removeModifier(TOWNSTEAD_FATIGUE_SPEED_UUID);
        if (penalty != 0.0) {
            speedAttr.addTransientModifier(new AttributeModifier(
                    TOWNSTEAD_FATIGUE_SPEED_UUID,
                    "townstead:fatigue_speed_penalty",
                    penalty,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
        *///?}
    }

    private static final class TickState {
        private int lastSyncedFatigue = -1;
        private boolean lastSyncedCollapsed = false;
        private double lastPenalty = 0.0;
        private float fatigueResidue = 0f;
        private long lastFatigueDayTime = -1;
        private long lastCollapsedGameTime = -1;
        private long lastMoodDayTime = -1;
        private Schedule preOverrideSchedule = null;
    }
}
