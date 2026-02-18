package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.hunger.VillagerEatingManager;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.component.DataComponents;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HungerVillagerTicker {
    private static final ResourceLocation TOWNSTEAD_SPEED_PENALTY =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "hunger_speed_penalty");

    private static final Map<Integer, TickState> STATE = new ConcurrentHashMap<>();

    private HungerVillagerTicker() {}

    public static void tick(VillagerEntityMCA self) {
        if (!(self.level() instanceof ServerLevel level)) return;

        TickState state = STATE.computeIfAbsent(self.getId(), id -> new TickState());
        CompoundTag hunger = self.getData(Townstead.HUNGER_DATA);
        boolean hungerChanged = VillagerEatingManager.tickAndFinalize(self, hunger);

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
            HungerData.setExhaustion(hunger, HungerData.getExhaustion(hunger) + dist * HungerData.EXHAUSTION_MOVEMENT_PER_BLOCK);
        }

        VillagerBrain<?> brain = self.getVillagerBrain();
        Chore currentJob = brain.getCurrentJob();
        if (brain.isPanicking() || self.getLastHurtByMob() != null) {
            HungerData.setExhaustion(hunger, HungerData.getExhaustion(hunger) + HungerData.EXHAUSTION_COMBAT);
        } else if (currentJob != Chore.NONE) {
            HungerData.setExhaustion(hunger, HungerData.getExhaustion(hunger) + HungerData.EXHAUSTION_CHORE);
        } else if (isGuardPatrolling(self)) {
            HungerData.setExhaustion(hunger, HungerData.getExhaustion(hunger) + HungerData.EXHAUSTION_GUARD_PATROL);
        } else if (!isResting(self)) {
            HungerData.setExhaustion(hunger, HungerData.getExhaustion(hunger) + HungerData.EXHAUSTION_AWAKE_BASELINE);
        }

        hungerChanged |= HungerData.processExhaustion(hunger);
        if (!isResting(self) && self.tickCount % HungerData.PASSIVE_DRAIN_INTERVAL == 0) {
            hungerChanged |= HungerData.passiveDrain(hunger);
        }

        Activity currentActivity = currentScheduleActivity(self);
        if (state.lastActivity != null && currentActivity != state.lastActivity) {
            int h = HungerData.getHunger(hunger);
            long gameTime = self.level().getGameTime();
            long lastAte = HungerData.getLastAteTime(hunger);
            boolean canEat = (gameTime - lastAte) >= HungerData.MIN_EAT_INTERVAL && !VillagerEatingManager.isEating(self);
            if (canEat) {
                boolean shouldEat = false;
                if (state.lastActivity == Activity.REST && h < HungerData.BREAKFAST_THRESHOLD) {
                    shouldEat = true;
                } else if (state.lastActivity == Activity.WORK && h < HungerData.LUNCH_THRESHOLD) {
                    shouldEat = true;
                } else if (currentActivity == Activity.REST && h < HungerData.DINNER_THRESHOLD) {
                    shouldEat = true;
                }
                if (shouldEat) hungerChanged |= tryEatFromInventory(self);
            }
        }
        state.lastActivity = currentActivity;

        if (HungerData.getHunger(hunger) < HungerData.ADEQUATE_THRESHOLD) {
            long gameTime = self.level().getGameTime();
            long lastAte = HungerData.getLastAteTime(hunger);
            long minEatInterval = HungerData.isEatingMode(hunger)
                    ? 20L
                    : (HungerData.getHunger(hunger) < HungerData.EMERGENCY_THRESHOLD ? 20L : HungerData.MIN_EAT_INTERVAL);
            if ((gameTime - lastAte) >= minEatInterval && !VillagerEatingManager.isEating(self)) {
                hungerChanged |= tryEatFromInventory(self);
            }
        }

        if (self.tickCount % HungerData.MOOD_CHECK_INTERVAL == 0) {
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

        updateSpeedModifier(self, HungerData.getHunger(hunger));
        self.setData(Townstead.HUNGER_DATA, hunger);

        int currentHunger = HungerData.getHunger(hunger);
        if (currentHunger != state.lastSyncedHunger || hungerChanged) {
            state.lastSyncedHunger = currentHunger;
            PacketDistributor.sendToPlayersTrackingEntity(self, Townstead.townstead$hungerSync(self, hunger));
        }

        if (!self.isAlive() || self.isRemoved()) {
            STATE.remove(self.getId());
        }
    }

    private static boolean isGuardPatrolling(VillagerEntityMCA self) {
        var profession = self.getVillagerData().getProfession();
        return (profession == ProfessionsMCA.GUARD || profession == ProfessionsMCA.ARCHER)
                && currentScheduleActivity(self) == Activity.WORK;
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
        FoodProperties props = food.get(DataComponents.FOOD);
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
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food != null && food.nutrition() > bestNutrition) {
                bestNutrition = food.nutrition();
                best = stack;
            }
        }
        return best;
    }

    private static void updateSpeedModifier(VillagerEntityMCA self, int currentHunger) {
        AttributeInstance speedAttr = self.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;
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
    }

    private static final class TickState {
        private double prevX;
        private double prevZ;
        private Activity lastActivity;
        private int lastSyncedHunger = -1;
    }
}
