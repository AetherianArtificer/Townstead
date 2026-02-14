package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.hunger.HungerSyncPayload;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntityMCA.class)
public abstract class VillagerHungerMixin extends Villager {

    @Unique private double townstead$prevX;
    @Unique private double townstead$prevZ;
    @Unique private Activity townstead$lastActivity;
    @Unique private int townstead$lastSyncedHunger = -1;

    private static final ResourceLocation TOWNSTEAD_SPEED_PENALTY =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "hunger_speed_penalty");

    // Mixin requires a constructor but it's never called
    private VillagerHungerMixin() {
        super(null, null);
    }

    /**
     * Pick up hunger data injected by the editor into MCA's sync NBT.
     * Only fires when the editor's custom keys are present.
     */
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void townstead$readEditorHunger(CompoundTag nbt, CallbackInfo ci) {
        if (!nbt.contains(HungerData.EDITOR_KEY_HUNGER)) return;
        VillagerEntityMCA self = (VillagerEntityMCA)(Object)this;
        CompoundTag hunger = self.getData(Townstead.HUNGER_DATA);
        HungerData.setHunger(hunger, nbt.getInt(HungerData.EDITOR_KEY_HUNGER));
        HungerData.setSaturation(hunger, nbt.getFloat(HungerData.EDITOR_KEY_SATURATION));
        HungerData.setExhaustion(hunger, nbt.getFloat(HungerData.EDITOR_KEY_EXHAUSTION));
        self.setData(Townstead.HUNGER_DATA, hunger);
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void townstead$hungerTick(CallbackInfo ci) {
        if (level().isClientSide) return;

        VillagerEntityMCA self = (VillagerEntityMCA)(Object)this;
        CompoundTag hunger = self.getData(Townstead.HUNGER_DATA);

        // --- 1. Movement exhaustion ---
        double dx = getX() - townstead$prevX;
        double dz = getZ() - townstead$prevZ;
        double distSq = dx * dx + dz * dz;
        townstead$prevX = getX();
        townstead$prevZ = getZ();
        if (distSq > 0.0025) { // ~0.05 blocks minimum to count
            float dist = (float) Math.sqrt(distSq);
            HungerData.setExhaustion(hunger, HungerData.getExhaustion(hunger)
                    + dist * HungerData.EXHAUSTION_MOVEMENT_PER_BLOCK);
        }

        // --- 2. Activity-based exhaustion ---
        VillagerBrain<?> brain = self.getVillagerBrain();
        Chore currentJob = brain.getCurrentJob();

        if (brain.isPanicking() || self.getLastHurtByMob() != null) {
            // Combat / panic
            HungerData.setExhaustion(hunger, HungerData.getExhaustion(hunger) + HungerData.EXHAUSTION_COMBAT);
        } else if (currentJob != Chore.NONE) {
            // Active chore
            HungerData.setExhaustion(hunger, HungerData.getExhaustion(hunger) + HungerData.EXHAUSTION_CHORE);
        } else if (townstead$isGuardPatrolling(self)) {
            // Guard on patrol (WORK activity, no chore assigned)
            HungerData.setExhaustion(hunger, HungerData.getExhaustion(hunger) + HungerData.EXHAUSTION_GUARD_PATROL);
        } else if (!townstead$isResting(self)) {
            // Awake baseline
            HungerData.setExhaustion(hunger, HungerData.getExhaustion(hunger) + HungerData.EXHAUSTION_AWAKE_BASELINE);
        }

        // --- 3. Process exhaustion pipeline ---
        boolean hungerChanged = HungerData.processExhaustion(hunger);

        // --- 3b. Passive metabolic drain (time-based) ---
        if (!townstead$isResting(self)
                && tickCount % HungerData.PASSIVE_DRAIN_INTERVAL == 0) {
            hungerChanged |= HungerData.passiveDrain(hunger);
        }

        // --- 4. Meal scheduling ---
        Activity currentActivity = townstead$getCurrentScheduleActivity(self);
        if (townstead$lastActivity != null && currentActivity != townstead$lastActivity) {
            int h = HungerData.getHunger(hunger);
            long gameTime = level().getGameTime();
            long lastAte = HungerData.getLastAteTime(hunger);
            boolean canEat = (gameTime - lastAte) >= HungerData.MIN_EAT_INTERVAL;

            if (canEat) {
                boolean shouldEat = false;
                // Breakfast: REST -> anything else
                if (townstead$lastActivity == Activity.REST && h < HungerData.BREAKFAST_THRESHOLD) {
                    shouldEat = true;
                }
                // Lunch: WORK -> MEET (or any non-work transition from work)
                else if (townstead$lastActivity == Activity.WORK && h < HungerData.LUNCH_THRESHOLD) {
                    shouldEat = true;
                }
                // Dinner: any -> REST
                else if (currentActivity == Activity.REST && h < HungerData.DINNER_THRESHOLD) {
                    shouldEat = true;
                }

                if (shouldEat) {
                    hungerChanged |= townstead$tryEat(self, hunger);
                }
            }
        }
        townstead$lastActivity = currentActivity;

        // --- 5. Emergency eating ---
        if (HungerData.getHunger(hunger) < HungerData.EMERGENCY_THRESHOLD) {
            long gameTime = level().getGameTime();
            long lastAte = HungerData.getLastAteTime(hunger);
            if ((gameTime - lastAte) >= HungerData.MIN_EAT_INTERVAL) {
                hungerChanged |= townstead$tryEat(self, hunger);
            }
        }

        // --- 6. Mood pressure (every 1200 ticks) ---
        if (tickCount % HungerData.MOOD_CHECK_INTERVAL == 0) {
            int h = HungerData.getHunger(hunger);
            HungerData.HungerState state = HungerData.getState(h);
            int moodDelta = HungerData.getMoodPressure(state);
            if (moodDelta != 0) {
                brain.modifyMoodValue(moodDelta);
            }
        }

        // --- 7. Speed modifier ---
        townstead$updateSpeedModifier(HungerData.getHunger(hunger));

        // --- 8. Persist and sync ---
        // Always persist — getData() may return a copy, so in-place edits need setData()
        self.setData(Townstead.HUNGER_DATA, hunger);

        int currentHunger = HungerData.getHunger(hunger);
        if (currentHunger != townstead$lastSyncedHunger) {
            townstead$lastSyncedHunger = currentHunger;
            if (level() instanceof ServerLevel) {
                PacketDistributor.sendToPlayersTrackingEntity(
                        self,
                        new HungerSyncPayload(self.getId(), currentHunger)
                );
            }
        }
    }

    @Unique
    private boolean townstead$isGuardPatrolling(VillagerEntityMCA self) {
        var profession = self.getVillagerData().getProfession();
        return (profession == ProfessionsMCA.GUARD || profession == ProfessionsMCA.ARCHER)
                && townstead$getCurrentScheduleActivity(self) == Activity.WORK;
    }

    @Unique
    private boolean townstead$isResting(VillagerEntityMCA self) {
        return townstead$getCurrentScheduleActivity(self) == Activity.REST;
    }

    @Unique
    private Activity townstead$getCurrentScheduleActivity(VillagerEntityMCA self) {
        long dayTime = level().getDayTime() % 24000L;
        return self.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    @Unique
    private boolean townstead$tryEat(VillagerEntityMCA self, CompoundTag hunger) {
        // Try villager's own inventory first
        ItemStack food = townstead$findBestFood(self.getInventory());
        if (!food.isEmpty()) {
            return townstead$consumeFood(food, hunger);
        }

        // Try nearby containers
        food = townstead$findFoodInNearbyContainers(self);
        if (!food.isEmpty()) {
            return townstead$consumeFood(food, hunger);
        }

        return false;
    }

    @Unique
    private boolean townstead$consumeFood(ItemStack food, CompoundTag hunger) {
        FoodProperties props = food.get(DataComponents.FOOD);
        if (props == null) return false;

        food.shrink(1);
        HungerData.applyFood(hunger, props);
        HungerData.setLastAteTime(hunger, level().getGameTime());
        return true;
    }

    @Unique
    private ItemStack townstead$findBestFood(SimpleContainer inventory) {
        ItemStack best = ItemStack.EMPTY;
        int bestNutrition = 0;
        int bestSlot = -1;

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food != null && food.nutrition() > bestNutrition) {
                bestNutrition = food.nutrition();
                best = stack;
                bestSlot = i;
            }
        }
        return best;
    }

    @Unique
    private ItemStack townstead$findFoodInNearbyContainers(VillagerEntityMCA self) {
        BlockPos center = self.blockPosition();
        ItemStack best = ItemStack.EMPTY;
        int bestNutrition = 0;
        Container bestContainer = null;
        int bestSlot = -1;

        // Scan 16-block horizontal radius, 4-block vertical
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-16, -4, -16),
                center.offset(16, 4, 16))) {

            BlockEntity be = level().getBlockEntity(pos);
            if (!(be instanceof Container container)) continue;

            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                FoodProperties food = stack.get(DataComponents.FOOD);
                if (food != null && food.nutrition() > bestNutrition) {
                    bestNutrition = food.nutrition();
                    best = stack;
                    bestContainer = container;
                    bestSlot = i;
                }
            }
        }

        return best; // Shrink will be called on the actual stack reference from the container
    }

    @Unique
    private void townstead$updateSpeedModifier(int currentHunger) {
        AttributeInstance speedAttr = getAttribute(Attributes.MOVEMENT_SPEED);
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
        } else {
            if (existing != null) {
                speedAttr.removeModifier(TOWNSTEAD_SPEED_PENALTY);
            }
        }
    }
}
