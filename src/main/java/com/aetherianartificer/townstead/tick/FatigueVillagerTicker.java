package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.aetherianartificer.townstead.fatigue.FatigueData;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.schedule.Activity;
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

    public static void tick(VillagerEntityMCA self) {
        if (!(self.level() instanceof ServerLevel level)) return;
        if (!TownsteadConfig.isVillagerFatigueEnabled()) return;

        //? if neoforge {
        CompoundTag fatigue = self.getData(Townstead.FATIGUE_DATA);
        //?} else {
        /*CompoundTag fatigue = self.getPersistentData().getCompound("townstead_fatigue");
        *///?}

        TickState state = STATE.computeIfAbsent(self.getId(), id -> new TickState());
        int oldFatigue = FatigueData.getFatigue(fatigue);
        boolean changed = false;

        // --- Collapse enforcement (every tick) ---
        if (FatigueData.isCollapsed(fatigue)) {
            // Collapsed villagers cannot move — erase movement memories
            self.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            self.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            self.getNavigation().stop();
            // Force SLEEPING pose for visual rotation (lying on back).
            // Don't use startSleeping() — it repositions to bed height and floats.
            if (!self.hasPose(net.minecraft.world.entity.Pose.SLEEPING)) {
                self.setPose(net.minecraft.world.entity.Pose.SLEEPING);
                self.refreshDimensions();
            }
        } else if (state.wasCollapsed) {
            // Collapse just ended — restore standing pose
            if (self.hasPose(net.minecraft.world.entity.Pose.SLEEPING) && !self.isSleeping()) {
                self.setPose(net.minecraft.world.entity.Pose.STANDING);
                self.refreshDimensions();
            }
        }
        state.wasCollapsed = FatigueData.isCollapsed(fatigue);

        // --- Accumulation / recovery on interval ---
        if (self.tickCount % FatigueData.ACCUMULATION_INTERVAL == 0) {
            boolean isNocturnal = isNocturnal(self);
            boolean inBed = self.isSleeping();
            Activity activity = currentScheduleActivity(self);
            boolean inCombat = self.getVillagerBrain().isPanicking()
                    || self.getLastHurtByMob() != null;
            long dayTime = level.getDayTime() % 24000L;
            boolean isCycleAligned = isCycleAligned(isNocturnal, dayTime);

            if (FatigueData.isCollapsed(fatigue)) {
                // Collapsed: slow passive recovery + try auto-drinking coffee
                applyFatigueDelta(fatigue, state, FatigueData.RECOVERY_COLLAPSED);
                FatigueData.tryAutoDrinkCoffee(self);
            } else if (inBed) {
                // In bed: recovery based on cycle alignment
                float recovery = isCycleAligned
                        ? FatigueData.RECOVERY_BED_ALIGNED
                        : FatigueData.RECOVERY_BED_MISALIGNED;
                applyFatigueDelta(fatigue, state, recovery);
            } else if (activity == Activity.REST) {
                // REST without bed: tiny recovery
                applyFatigueDelta(fatigue, state, FatigueData.RECOVERY_REST_NO_BED);
            } else {
                // Accumulation based on activity
                float rate;
                if (activity == Activity.WORK) {
                    rate = FatigueData.RATE_WORK;
                } else if (activity == Activity.MEET) {
                    rate = FatigueData.RATE_MEET;
                } else {
                    rate = FatigueData.RATE_IDLE;
                }

                // Combat multiplier
                if (inCombat) {
                    rate *= FatigueData.COMBAT_MULTIPLIER;
                }

                // Cycle alignment multiplier (from config)
                float alignedMult = TownsteadConfig.FATIGUE_NOCTURNAL_MULTIPLIER.get().floatValue();
                float misalignedMult = TownsteadConfig.FATIGUE_MISALIGNED_MULTIPLIER.get().floatValue();
                if (isCycleAligned) {
                    rate *= alignedMult;
                } else {
                    rate *= misalignedMult;
                }

                applyFatigueDelta(fatigue, state, rate);
            }

            changed = FatigueData.getFatigue(fatigue) != oldFatigue;

            int currentFatigue = FatigueData.getFatigue(fatigue);
            int collapseThreshold = TownsteadConfig.FATIGUE_COLLAPSE_THRESHOLD.get();
            int recoveryGate = TownsteadConfig.FATIGUE_RECOVERY_GATE.get();

            // --- Collapse check ---
            if (currentFatigue >= collapseThreshold && !inBed && !FatigueData.isCollapsed(fatigue)) {
                FatigueData.setCollapsed(fatigue, true);
                FatigueData.setGated(fatigue, true);
                changed = true;
            }

            // --- Gate release check ---
            if (currentFatigue < recoveryGate && FatigueData.isGated(fatigue)) {
                FatigueData.setGated(fatigue, false);
                FatigueData.setCollapsed(fatigue, false);
                changed = true;
            }

            // --- Auto-drink coffee when drowsy or worse ---
            currentFatigue = FatigueData.getFatigue(fatigue);
            if (currentFatigue >= FatigueData.DROWSY_THRESHOLD) {
                if (FatigueData.tryAutoDrinkCoffee(self)) {
                    changed = true;
                }
            }
        }

        // --- Mood drift (every 2400 ticks) ---
        if (self.tickCount % FatigueData.MOOD_CHECK_INTERVAL == 0) {
            int f = FatigueData.getFatigue(fatigue);
            FatigueData.FatigueState fatigueState = FatigueData.getState(f);
            float pressure = FatigueData.getMoodPressure(fatigueState);
            float drift = FatigueData.getMoodDrift(fatigue) + pressure;
            int moodDelta = 0;
            if (drift >= 1f) moodDelta = (int) Math.floor(drift);
            else if (drift <= -1f) moodDelta = (int) Math.ceil(drift);

            if (moodDelta != 0) {
                self.getVillagerBrain().modifyMoodValue(moodDelta);
                drift -= moodDelta;
            }
            FatigueData.setMoodDrift(fatigue, drift);
        }

        // --- Speed modifier ---
        updateSpeedModifier(self, FatigueData.getFatigue(fatigue), state);

        // --- Persist ---
        //? if neoforge {
        self.setData(Townstead.FATIGUE_DATA, fatigue);
        //?} else {
        /*self.getPersistentData().put("townstead_fatigue", fatigue);
        *///?}

        // --- Sync ---
        int currentFatigue = FatigueData.getFatigue(fatigue);
        boolean currentCollapsed = FatigueData.isCollapsed(fatigue);
        if (currentFatigue != state.lastSyncedFatigue || currentCollapsed != state.lastSyncedCollapsed) {
            state.lastSyncedFatigue = currentFatigue;
            state.lastSyncedCollapsed = currentCollapsed;
            //? if neoforge {
            PacketDistributor.sendToPlayersTrackingEntity(self, Townstead.townstead$fatigueSync(self, fatigue));
            //?} else if forge {
            /*TownsteadNetwork.sendToTrackingEntity(self, Townstead.townstead$fatigueSync(self, fatigue));
            *///?}
        }

        // --- Cleanup ---
        if (!self.isAlive() || self.isRemoved()) {
            STATE.remove(self.getId());
        }
    }

    private static boolean isNocturnal(VillagerEntityMCA self) {
        VillagerBrain<?> brain = self.getVillagerBrain();
        Personality personality = brain.getPersonality();
        //? if neoforge {
        return personality == Personality.ODD
                || personality == Personality.GLOOMY
                || personality == Personality.INTROVERTED;
        //?} else {
        /*return personality == Personality.ODD
                || personality == Personality.GLOOMY
                || personality == Personality.SHY;
        *///?}
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
    private static void applyFatigueDelta(CompoundTag fatigue, TickState state, float delta) {
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
        int current = FatigueData.getFatigue(fatigue);
        int newValue = Math.max(0, Math.min(current + wholeDelta, FatigueData.MAX_FATIGUE));
        FatigueData.setFatigue(fatigue, newValue);
    }

    private static Activity currentScheduleActivity(VillagerEntityMCA self) {
        long dayTime = self.level().getDayTime() % 24000L;
        return self.getBrain().getSchedule().getActivityAt((int) dayTime);
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
        private boolean wasCollapsed = false;
        private double lastPenalty = 0.0;
        private float fatigueResidue = 0f;
    }
}
