package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.hunger.FoodSafety;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.hunger.VillagerEatingManager;
import com.aetherianartificer.townstead.thirst.VillagerDrinkingManager;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.registry.ProfessionsMCA;
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
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
        //? if neoforge {
        CompoundTag hunger = self.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag hunger = self.getPersistentData().getCompound("townstead_hunger");
        *///?}
        boolean hungerChanged = VillagerEatingManager.tickAndFinalize(self, hunger);

        long dayTime = level.getDayTime();
        if (state.lastDayTime < 0) state.lastDayTime = dayTime;
        long dayTimeDelta = Math.max(0, dayTime - state.lastDayTime);
        state.lastDayTime = dayTime;
        int currentHungerLevel = HungerData.getHunger(hunger);
        if (HungerData.isEatingMode(hunger)) {
            if (currentHungerLevel >= HungerData.ADEQUATE_THRESHOLD) HungerData.setEatingMode(hunger, false);
        } else if (currentHungerLevel < HungerData.EMERGENCY_THRESHOLD) {
            HungerData.setEatingMode(hunger, true);
        }

        double dx = self.getX() - state.prevX;
        double dz = self.getZ() - state.prevZ;
        double distSq = dx * dx + dz * dz;
        state.prevX = self.getX();
        state.prevZ = self.getZ();
        if (distSq > 0.0025) {
            float dist = (float) Math.sqrt(distSq);
            HungerData.setExhaustion(hunger, HungerData.getExhaustion(hunger) + dist * HungerData.EXHAUSTION_MOVEMENT_PER_BLOCK * dayTimeDelta);
        }

        VillagerBrain<?> brain = self.getVillagerBrain();
        Chore currentJob = brain.getCurrentJob();
        if (brain.isPanicking() || self.getLastHurtByMob() != null) {
            HungerData.setExhaustion(hunger, HungerData.getExhaustion(hunger) + HungerData.EXHAUSTION_COMBAT * dayTimeDelta);
        } else if (currentJob != Chore.NONE) {
            HungerData.setExhaustion(hunger, HungerData.getExhaustion(hunger) + HungerData.EXHAUSTION_CHORE * dayTimeDelta);
        } else if (isGuardPatrolling(self)) {
            HungerData.setExhaustion(hunger, HungerData.getExhaustion(hunger) + HungerData.EXHAUSTION_GUARD_PATROL * dayTimeDelta);
        } else if (!isResting(self)) {
            HungerData.setExhaustion(hunger, HungerData.getExhaustion(hunger) + HungerData.EXHAUSTION_AWAKE_BASELINE * dayTimeDelta);
        }

        hungerChanged |= HungerData.processExhaustion(hunger);
        // Drowsy/exhausted villagers drain hunger 1.25x faster (shorter interval)
        int passiveInterval = HungerData.PASSIVE_DRAIN_INTERVAL;
        if (TownsteadConfig.isVillagerFatigueEnabled()) {
            //? if neoforge {
            CompoundTag fatigueTag = self.getData(Townstead.FATIGUE_DATA);
            //?} else {
            /*CompoundTag fatigueTag = self.getPersistentData().getCompound("townstead_fatigue");
            *///?}
            int fatigue = FatigueData.getFatigue(fatigueTag);
            if (fatigue >= FatigueData.DROWSY_THRESHOLD) {
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
            boolean drained = false;
            int drainIterations = 0;
            while (dayTime - state.lastPassiveDrainDayTime >= passiveInterval && drainIterations < 100) {
                state.lastPassiveDrainDayTime += passiveInterval;
                hungerChanged |= HungerData.passiveDrain(hunger);
                drained = true;
                drainIterations++;
            }

            // Activity-gated eating: check on passive drain interval, guarded by MIN_EAT_INTERVAL
            if (drained) {
                int h = HungerData.getHunger(hunger);
                int threshold = (currentActivity == Activity.IDLE || currentActivity == Activity.MEET)
                        ? HungerData.LUNCH_THRESHOLD
                        : HungerData.EMERGENCY_THRESHOLD;
                if (h < threshold) {
                    long gameTime = level.getGameTime();
                    long lastAte = HungerData.getLastAteTime(hunger);
                    if ((gameTime - lastAte) >= HungerData.MIN_EAT_INTERVAL
                            && !VillagerEatingManager.isEating(self)
                            && !VillagerDrinkingManager.isDrinking(self)) {
                        hungerChanged |= tryEatFromInventory(self);
                    }
                }
            }
        }

        if (state.lastMoodDayTime < 0) state.lastMoodDayTime = dayTime;
        if (dayTime - state.lastMoodDayTime >= HungerData.MOOD_CHECK_INTERVAL) {
            state.lastMoodDayTime = dayTime;
            int h = HungerData.getHunger(hunger);
            HungerData.HungerState moodState = HungerData.getState(h);
            float pressure = HungerData.getMoodPressure(moodState);
            float drift = HungerData.getMoodDrift(hunger) + pressure;
            int moodDelta = 0;
            if (drift >= 1f) moodDelta = (int) Math.floor(drift);
            else if (drift <= -1f) moodDelta = (int) Math.ceil(drift);

            if (moodDelta != 0) {
                brain.modifyMoodValue(moodDelta);
                drift -= moodDelta;
            }
            HungerData.setMoodDrift(hunger, drift);
        }

        if (!self.isBaby()) {
            updateSpeedModifier(self, HungerData.getHunger(hunger));
        }
        //? if neoforge {
        self.setData(Townstead.HUNGER_DATA, hunger);
        //?} else {
        /*self.getPersistentData().put("townstead_hunger", hunger);
        *///?}

        int currentHunger = HungerData.getHunger(hunger);
        if (currentHunger != state.lastSyncedHunger || hungerChanged) {
            state.lastSyncedHunger = currentHunger;
            //? if neoforge {
            PacketDistributor.sendToPlayersTrackingEntity(self, Townstead.townstead$hungerSync(self, hunger));
            //?} else if forge {
            /*TownsteadNetwork.sendToTrackingEntity(self, Townstead.townstead$hungerSync(self, hunger));
            *///?}
        }

        if (!self.isAlive() || self.isRemoved()) {
            STATE.remove(self.getId());
        }
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

    private static boolean tryEatFromInventory(VillagerEntityMCA self) {
        if (!TownsteadConfig.ENABLE_SELF_INVENTORY_EATING.get()) return false;
        ItemStack food = findBestFood(self.getInventory());
        if (food.isEmpty()) return false;
        return consumeFood(self, food);
    }

    private static boolean consumeFood(VillagerEntityMCA self, ItemStack food) {
        //? if >=1.21 {
        FoodProperties props = food.get(DataComponents.FOOD);
        //?} else {
        /*FoodProperties props = food.getFoodProperties(null);
        *///?}
        if (props == null) return false;
        if (!VillagerEatingManager.startEating(self, food)) return false;
        food.shrink(1);
        return true;
    }

    private static ItemStack findBestFood(SimpleContainer inventory) {
        ItemStack best = ItemStack.EMPTY;
        int bestNutrition = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            // Skip anything harmful (pufferfish, spider eye, rotten flesh, etc.)
            // so the passive-hunger ticker doesn't silently drop puffers into
            // the void via startEating → shrink(1) → mixin-blocked eat.
            if (!FoodSafety.isSafeNutritiousFood(stack)) continue;
            //? if >=1.21 {
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food.nutrition() > bestNutrition) {
                bestNutrition = food.nutrition();
            //?} else {
            /*FoodProperties food = stack.getFoodProperties(null);
            if (food.getNutrition() > bestNutrition) {
                bestNutrition = food.getNutrition();
            *///?}
                best = stack;
            }
        }
        return best;
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
