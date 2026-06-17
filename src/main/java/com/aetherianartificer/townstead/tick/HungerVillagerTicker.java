package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.hunger.VillagerConsumptionManager;
import com.aetherianartificer.townstead.origin.needs.NeedSuppression;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.schedule.Activity;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HungerVillagerTicker {
    //? if >=1.21 {
    private static final ResourceLocation TOWNSTEAD_SPEED_PENALTY =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "hunger_speed_penalty");
    //?} else {
    /*private static final ResourceLocation TOWNSTEAD_SPEED_PENALTY =
            new ResourceLocation(Townstead.MOD_ID, "hunger_speed_penalty");
    *///?}
    //? if forge {
    /*private static final java.util.UUID TOWNSTEAD_SPEED_PENALTY_UUID =
            java.util.UUID.nameUUIDFromBytes("townstead:hunger_speed_penalty".getBytes());
    *///?}

    private static final Map<Integer, TickState> STATE = new ConcurrentHashMap<>();
    private HungerVillagerTicker() {}

    public static void tick(VillagerEntityMCA self) {
        if (!(self.level() instanceof ServerLevel level)) return;
        if (!TownsteadConfig.isVillagerHungerEnabled()) {
            removeSpeedModifier(self);
            return;
        }

        TickState state = STATE.computeIfAbsent(self.getId(), id -> new TickState());
        TownsteadVillager.Needs needs = TownsteadVillagers.get(self).needs();

        // A diet:"none" race does not eat: pin hunger full so it never decays and the refuel task
        // never fires, and remove any lingering hunger speed penalty. The interact screen hides the
        // icon client-side from the same diet gene.
        if (NeedSuppression.suppressesHunger(self)) {
            needs.setEatingMode(false);
            if (needs.hunger() != HungerData.MAX_HUNGER) needs.setHunger(HungerData.MAX_HUNGER);
            removeSpeedModifier(self);
            int pinned = needs.hunger();
            if (pinned != state.lastSyncedHunger) {
                state.lastSyncedHunger = pinned;
                pushHunger(self, needs.hungerTag());
            }
            return;
        }

        boolean hungerChanged = VillagerConsumptionManager.tickAndFinalize(self, needs);

        long dayTime = level.getDayTime();
        if (state.lastDayTime < 0) state.lastDayTime = dayTime;
        long dayTimeDelta = Math.max(0, dayTime - state.lastDayTime);
        state.lastDayTime = dayTime;
        int currentHungerLevel = needs.hunger();
        if (needs.eatingMode()) {
            if (currentHungerLevel >= HungerData.ADEQUATE_THRESHOLD) needs.setEatingMode(false);
        } else if (currentHungerLevel < HungerData.EMERGENCY_THRESHOLD) {
            needs.setEatingMode(true);
        }

        double dx = self.getX() - state.prevX;
        double dz = self.getZ() - state.prevZ;
        double distSq = dx * dx + dz * dz;
        state.prevX = self.getX();
        state.prevZ = self.getZ();
        if (distSq > 0.0025) {
            float dist = (float) Math.sqrt(distSq);
            needs.addHungerExhaustion(dist * HungerData.EXHAUSTION_MOVEMENT_PER_BLOCK * dayTimeDelta);
        }

        VillagerBrain<?> brain = self.getVillagerBrain();
        Chore currentJob = brain.getCurrentJob();
        if (brain.isPanicking() || self.getLastHurtByMob() != null) {
            needs.addHungerExhaustion(HungerData.EXHAUSTION_COMBAT * dayTimeDelta);
        } else if (currentJob != Chore.NONE) {
            needs.addHungerExhaustion(HungerData.EXHAUSTION_CHORE * dayTimeDelta);
        } else if (isGuardPatrolling(self)) {
            needs.addHungerExhaustion(HungerData.EXHAUSTION_GUARD_PATROL * dayTimeDelta);
        } else if (!isResting(self)) {
            needs.addHungerExhaustion(HungerData.EXHAUSTION_AWAKE_BASELINE * dayTimeDelta);
        }

        hungerChanged |= needs.processHungerExhaustion();
        // Drowsy/exhausted villagers drain hunger 1.25x faster (shorter interval)
        int passiveInterval = HungerData.PASSIVE_DRAIN_INTERVAL;
        if (TownsteadConfig.isVillagerFatigueEnabled()) {
            if (needs.fatigue() >= FatigueData.DROWSY_THRESHOLD) {
                passiveInterval = (int)(passiveInterval / FatigueData.DROWSY_HUNGER_MULTIPLIER);
            }
        }
        if (state.lastPassiveDrainDayTime < 0) state.lastPassiveDrainDayTime = dayTime;
        Activity currentActivity = currentScheduleActivity(self);
        boolean resting = currentActivity == Activity.REST;
        if (resting) {
            // Keep tracking current while resting so wake-up doesn't cause burst drain
            state.lastPassiveDrainDayTime = dayTime;
        } else {
            // Passive hunger drain only. Eating is owned by RefuelTask now.
            int drainIterations = 0;
            while (dayTime - state.lastPassiveDrainDayTime >= passiveInterval && drainIterations < 100) {
                state.lastPassiveDrainDayTime += passiveInterval;
                hungerChanged |= needs.passiveHungerDrain();
                drainIterations++;
            }
        }

        if (state.lastMoodDayTime < 0) state.lastMoodDayTime = dayTime;
        if (dayTime - state.lastMoodDayTime >= HungerData.MOOD_CHECK_INTERVAL) {
            state.lastMoodDayTime = dayTime;
            int h = needs.hunger();
            HungerData.HungerState moodState = HungerData.getState(h);
            float pressure = HungerData.getMoodPressure(moodState);
            float drift = needs.hungerMoodDrift() + pressure;
            int moodDelta = 0;
            if (drift >= 1f) moodDelta = (int) Math.floor(drift);
            else if (drift <= -1f) moodDelta = (int) Math.ceil(drift);

            if (moodDelta != 0) {
                brain.modifyMoodValue(moodDelta);
                drift -= moodDelta;
            }
            needs.setHungerMoodDrift(drift);
        }

        if (!self.isBaby()) {
            updateSpeedModifier(self, needs.hunger());
        }

        int currentHunger = needs.hunger();
        if (currentHunger != state.lastSyncedHunger || hungerChanged) {
            state.lastSyncedHunger = currentHunger;
            pushHunger(self, needs.hungerTag());
        }

        if (!self.isAlive() || self.isRemoved()) {
            STATE.remove(self.getId());
        }
    }

    private static void pushHunger(VillagerEntityMCA self, CompoundTag hunger) {
        //? if neoforge {
        PacketDistributor.sendToPlayersTrackingEntity(self, Townstead.townstead$hungerSync(self, hunger));
        //?} else if forge {
        /*TownsteadNetwork.sendToTrackingEntity(self, Townstead.townstead$hungerSync(self, hunger));
        *///?}
    }

    private static boolean isGuardPatrolling(VillagerEntityMCA self) {
        var profession = self.getVillagerData().getProfession();
        //? if neoforge {
        return (profession == ProfessionsMCA.GUARD || profession == ProfessionsMCA.ARCHER)
                && currentScheduleActivity(self) == Activity.WORK;
        //?} else {
        /*return (profession == ProfessionsMCA.GUARD.get() || profession == ProfessionsMCA.ARCHER.get())
                && currentScheduleActivity(self) == Activity.WORK;
        *///?}
    }

    private static boolean isResting(VillagerEntityMCA self) {
        return currentScheduleActivity(self) == Activity.REST;
    }

    private static Activity currentScheduleActivity(VillagerEntityMCA self) {
        long dayTime = self.level().getDayTime() % 24000L;
        return self.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    private static void updateSpeedModifier(VillagerEntityMCA self, int currentHunger) {
        AttributeInstance speedAttr = self.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;
        //? if >=1.21 {
        AttributeModifier existing = speedAttr.getModifier(TOWNSTEAD_SPEED_PENALTY);
        if (currentHunger < HungerData.SPEED_PENALTY_THRESHOLD) {
            if (existing == null) {
                speedAttr.addTransientModifier(new AttributeModifier(
                        TOWNSTEAD_SPEED_PENALTY,
                        HungerData.SPEED_PENALTY_AMOUNT,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
            return;
        }
        if (existing != null) speedAttr.removeModifier(TOWNSTEAD_SPEED_PENALTY);
        //?} else {
        /*AttributeModifier existing = speedAttr.getModifier(TOWNSTEAD_SPEED_PENALTY_UUID);
        if (currentHunger < HungerData.SPEED_PENALTY_THRESHOLD) {
            if (existing == null) {
                speedAttr.addTransientModifier(new AttributeModifier(
                        TOWNSTEAD_SPEED_PENALTY_UUID,
                        "townstead:hunger_speed_penalty",
                        HungerData.SPEED_PENALTY_AMOUNT,
                        AttributeModifier.Operation.MULTIPLY_TOTAL
                ));
            }
            return;
        }
        if (existing != null) speedAttr.removeModifier(TOWNSTEAD_SPEED_PENALTY_UUID);
        *///?}
    }

    private static void removeSpeedModifier(VillagerEntityMCA self) {
        AttributeInstance speedAttr = self.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;
        //? if >=1.21 {
        AttributeModifier existing = speedAttr.getModifier(TOWNSTEAD_SPEED_PENALTY);
        if (existing != null) speedAttr.removeModifier(TOWNSTEAD_SPEED_PENALTY);
        //?} else {
        /*AttributeModifier existing = speedAttr.getModifier(TOWNSTEAD_SPEED_PENALTY_UUID);
        if (existing != null) speedAttr.removeModifier(TOWNSTEAD_SPEED_PENALTY_UUID);
        *///?}
    }

    private static final class TickState {
        private double prevX;
        private double prevZ;
        private int lastSyncedHunger = -1;
        private long lastDayTime = -1;
        private long lastPassiveDrainDayTime = -1;
        private long lastMoodDayTime = -1;
    }
}
