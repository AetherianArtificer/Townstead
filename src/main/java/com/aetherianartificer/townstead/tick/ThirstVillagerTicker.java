package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.hunger.VillagerConsumptionManager;
import com.aetherianartificer.townstead.origin.needs.NeedSuppression;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.registry.ProfessionsMCA;
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

public final class ThirstVillagerTicker {
    //? if >=1.21 {
    private static final ResourceLocation TOWNSTEAD_SPEED_PENALTY =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "thirst_speed_penalty");
    //?} else {
    /*private static final ResourceLocation TOWNSTEAD_SPEED_PENALTY =
            new ResourceLocation(Townstead.MOD_ID, "thirst_speed_penalty");
    *///?}
    private static final long BIOME_MODIFIER_RESAMPLE_TICKS = 100L;
    //? if forge {
    /*private static final java.util.UUID TOWNSTEAD_SPEED_PENALTY_UUID =
            java.util.UUID.nameUUIDFromBytes("townstead:thirst_speed_penalty".getBytes());
    *///?}

    private static final Map<Integer, TickState> STATE = new ConcurrentHashMap<>();

    private ThirstVillagerTicker() {}

    public static void tick(VillagerEntityMCA self) {
        if (!(self.level() instanceof ServerLevel level)) return;
        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge == null || !TownsteadConfig.isVillagerThirstEnabled()) {
            removeSpeedModifier(self);
            return;
        }

        TickState state = STATE.computeIfAbsent(self.getId(), id -> new TickState());
        TownsteadVillager.Needs needs = TownsteadVillagers.get(self).needs();

        // A liquid:"none" race does not drink: pin thirst full so it never decays and the refuel task
        // never fires. The interact screen hides the icon client-side from the same hydration gene.
        if (NeedSuppression.suppressesThirst(self)) {
            needs.setDrinkingMode(false);
            if (needs.thirst() != ThirstData.MAX_THIRST) needs.setThirst(ThirstData.MAX_THIRST);
            if (needs.quenched() != ThirstData.MAX_THIRST) needs.setQuenched(ThirstData.MAX_THIRST);
            removeSpeedModifier(self);
            int pinnedThirst = needs.thirst();
            int pinnedQuenched = needs.quenched();
            if (pinnedThirst != state.lastSyncedThirst || pinnedQuenched != state.lastSyncedQuenched) {
                state.lastSyncedThirst = pinnedThirst;
                state.lastSyncedQuenched = pinnedQuenched;
                pushThirst(self, needs.thirstTag());
            }
            return;
        }

        long gameTime = level.getGameTime();

        if (gameTime >= state.nextBiomeModifierSampleTick) {
            state.biomeModifier = bridge.exhaustionBiomeModifier(level, self.blockPosition());
            state.nextBiomeModifierSampleTick = gameTime + BIOME_MODIFIER_RESAMPLE_TICKS;
        }
        float biomeModifier = state.biomeModifier;

        long dayTime = level.getDayTime();
        if (state.lastDayTime < 0) state.lastDayTime = dayTime;
        long dayTimeDelta = Math.max(0, dayTime - state.lastDayTime);
        state.lastDayTime = dayTime;

        boolean thirstChanged = VillagerConsumptionManager.tickAndFinalize(self, needs);

        int currentThirstLevel = needs.thirst();
        if (needs.drinkingMode()) {
            if (currentThirstLevel >= ThirstData.ADEQUATE_THRESHOLD) needs.setDrinkingMode(false);
        } else if (currentThirstLevel <= ThirstData.EMERGENCY_THRESHOLD) {
            needs.setDrinkingMode(true);
        }

        double dx = self.getX() - state.prevX;
        double dz = self.getZ() - state.prevZ;
        double distSq = dx * dx + dz * dz;
        state.prevX = self.getX();
        state.prevZ = self.getZ();
        if (distSq > 0.0025) {
            float dist = (float) Math.sqrt(distSq);
            needs.addThirstExhaustion(dist * ThirstData.EXHAUSTION_MOVEMENT_PER_BLOCK * biomeModifier * dayTimeDelta);
        }

        VillagerBrain<?> brain = self.getVillagerBrain();
        Chore currentJob = brain.getCurrentJob();
        if (brain.isPanicking() || self.getLastHurtByMob() != null) {
            needs.addThirstExhaustion(ThirstData.EXHAUSTION_COMBAT * biomeModifier * dayTimeDelta);
        } else if (currentJob != Chore.NONE) {
            needs.addThirstExhaustion(ThirstData.EXHAUSTION_CHORE * biomeModifier * dayTimeDelta);
        } else if (isGuardPatrolling(self)) {
            needs.addThirstExhaustion(ThirstData.EXHAUSTION_GUARD_PATROL * biomeModifier * dayTimeDelta);
        } else if (!isResting(self)) {
            needs.addThirstExhaustion(ThirstData.EXHAUSTION_AWAKE_BASELINE * biomeModifier * dayTimeDelta);
        }

        thirstChanged |= needs.processThirstExhaustion();
        if (state.lastPassiveDrainDayTime < 0) state.lastPassiveDrainDayTime = dayTime;
        Activity currentActivity = currentScheduleActivity(self);
        boolean resting = currentActivity == Activity.REST;
        if (resting) {
            // Keep tracking current while resting so wake-up doesn't cause burst drain
            state.lastPassiveDrainDayTime = dayTime;
        } else {
            // Passive thirst drain only. Drinking is owned by RefuelTask now.
            int drainIterations = 0;
            while (dayTime - state.lastPassiveDrainDayTime >= ThirstData.PASSIVE_DRAIN_INTERVAL && drainIterations < 100) {
                state.lastPassiveDrainDayTime += ThirstData.PASSIVE_DRAIN_INTERVAL;
                thirstChanged |= needs.passiveThirstDrain();
                drainIterations++;
            }
        }

        if (state.lastMoodDayTime < 0) state.lastMoodDayTime = dayTime;
        if (dayTime - state.lastMoodDayTime >= ThirstData.MOOD_CHECK_INTERVAL) {
            state.lastMoodDayTime = dayTime;
            int t = needs.thirst();
            ThirstData.ThirstState moodState = ThirstData.getState(t);
            float pressure = ThirstData.getMoodPressure(moodState);
            float drift = needs.thirstMoodDrift() + pressure;
            int moodDelta = 0;
            if (drift >= 1f) moodDelta = (int) Math.floor(drift);
            else if (drift <= -1f) moodDelta = (int) Math.ceil(drift);

            if (moodDelta != 0) {
                brain.modifyMoodValue(moodDelta);
                drift -= moodDelta;
            }
            needs.setThirstMoodDrift(drift);
        }

        // Dehydration no longer deals damage — villagers get speed penalties
        // and mood pressure instead, matching hunger's non-lethal approach.
        needs.setThirstDamageTimer(0);

        if (!self.isBaby()) {
            updateSpeedModifier(self, needs.thirst());
        }

        int thirstLevel = needs.thirst();
        int quenchedLevel = needs.quenched();
        if (thirstLevel != state.lastSyncedThirst || quenchedLevel != state.lastSyncedQuenched || thirstChanged) {
            state.lastSyncedThirst = thirstLevel;
            state.lastSyncedQuenched = quenchedLevel;
            pushThirst(self, needs.thirstTag());
        }

        if (!self.isAlive() || self.isRemoved()) {
            STATE.remove(self.getId());
        }
    }

    private static void pushThirst(VillagerEntityMCA self, net.minecraft.nbt.CompoundTag thirst) {
        //? if neoforge {
        PacketDistributor.sendToPlayersTrackingEntity(self, Townstead.townstead$thirstSync(self, thirst));
        //?} else if forge {
        /*TownsteadNetwork.sendToTrackingEntity(self, Townstead.townstead$thirstSync(self, thirst));
        *///?}
    }

    private static boolean shouldApplyDehydrationDamage(ServerLevel level) {
        if (level.getServer().isHardcore()) return true;
        return TownsteadConfig.isThirstLethalFallbackEnabled();
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

    private static void updateSpeedModifier(VillagerEntityMCA self, int currentThirst) {
        AttributeInstance speedAttr = self.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;
        //? if >=1.21 {
        AttributeModifier existing = speedAttr.getModifier(TOWNSTEAD_SPEED_PENALTY);
        if (currentThirst <= ThirstData.SPEED_PENALTY_THRESHOLD) {
            if (existing == null) {
                speedAttr.addTransientModifier(new AttributeModifier(
                        TOWNSTEAD_SPEED_PENALTY,
                        ThirstData.SPEED_PENALTY_AMOUNT,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
            return;
        }
        if (existing != null) speedAttr.removeModifier(TOWNSTEAD_SPEED_PENALTY);
        //?} else {
        /*AttributeModifier existing = speedAttr.getModifier(TOWNSTEAD_SPEED_PENALTY_UUID);
        if (currentThirst <= ThirstData.SPEED_PENALTY_THRESHOLD) {
            if (existing == null) {
                speedAttr.addTransientModifier(new AttributeModifier(
                        TOWNSTEAD_SPEED_PENALTY_UUID,
                        "townstead:thirst_speed_penalty",
                        ThirstData.SPEED_PENALTY_AMOUNT,
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
        private float biomeModifier = 1.0f;
        private long nextBiomeModifierSampleTick;
        private int lastSyncedThirst = -1;
        private int lastSyncedQuenched = -1;
        private long lastDayTime = -1;
        private long lastPassiveDrainDayTime = -1;
        private long lastMoodDayTime = -1;
    }
}
