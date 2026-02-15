package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.hunger.HungerSyncPayload;
import com.aetherianartificer.townstead.hunger.HarvestWorkTask;
import com.aetherianartificer.townstead.hunger.SeekFoodTask;
import com.aetherianartificer.townstead.hunger.VillagerEatingManager;
import com.aetherianartificer.townstead.hunger.CareForYoungTask;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
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
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.serialization.Dynamic;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
     * Register SeekFoodTask into the brain's CORE activity after MCA initializes it.
     * Covers initial entity creation via makeBrain.
     */
    @SuppressWarnings("unchecked")
    @Inject(method = "makeBrain", at = @At("RETURN"))
    private void townstead$registerSeekFoodOnCreate(Dynamic<?> dynamic, CallbackInfoReturnable<Brain<?>> cir) {
        townstead$addSeekFoodTask((Brain<VillagerEntityMCA>) cir.getReturnValue());
    }

    /**
     * Register SeekFoodTask into the brain's CORE activity after MCA re-initializes it.
     * Covers profession changes, age transitions, etc.
     */
    @SuppressWarnings("unchecked")
    @Inject(method = "refreshBrain", at = @At("TAIL"))
    private void townstead$registerSeekFood(ServerLevel world, CallbackInfo ci) {
        townstead$addSeekFoodTask((Brain<VillagerEntityMCA>) (Brain<?>) getBrain());
    }

    @Unique
    private static void townstead$addSeekFoodTask(Brain<VillagerEntityMCA> brain) {
        brain.addActivity(Activity.CORE,
                ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>>of(
                        Pair.of(70, new HarvestWorkTask()),
                        Pair.of(99, new SeekFoodTask()),
                        Pair.of(110, new CareForYoungTask())
                ));
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
        boolean hungerChanged = VillagerEatingManager.tickAndFinalize(self, hunger);

        // Persistent "eating mode": once famished/starving, keep eating focus
        // until at least ADEQUATE is reached.
        int currentHungerLevel = HungerData.getHunger(hunger);
        if (HungerData.isEatingMode(hunger)) {
            if (currentHungerLevel >= HungerData.ADEQUATE_THRESHOLD) {
                HungerData.setEatingMode(hunger, false);
            }
        } else if (currentHungerLevel < HungerData.EMERGENCY_THRESHOLD) {
            HungerData.setEatingMode(hunger, true);
        }

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

        // Farming is handled by HarvestWorkTask when farm assist is enabled.

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
        hungerChanged |= HungerData.processExhaustion(hunger);

        // --- 3b. Passive metabolic drain (time-based) ---
        if (!townstead$isResting(self)
                && tickCount % HungerData.PASSIVE_DRAIN_INTERVAL == 0) {
            hungerChanged |= HungerData.passiveDrain(hunger);
        }

        // --- 4. Meal scheduling (inventory-only, SeekFoodTask handles external sources) ---
        Activity currentActivity = townstead$getCurrentScheduleActivity(self);
        if (townstead$lastActivity != null && currentActivity != townstead$lastActivity) {
            int h = HungerData.getHunger(hunger);
            long gameTime = level().getGameTime();
            long lastAte = HungerData.getLastAteTime(hunger);
            boolean canEat = (gameTime - lastAte) >= HungerData.MIN_EAT_INTERVAL && !VillagerEatingManager.isEating(self);

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
                    hungerChanged |= townstead$tryEatFromInventory(self);
                }
            }
        }
        townstead$lastActivity = currentActivity;

        // --- 5. Targeted self-feeding fallback (inventory only) ---
        if (HungerData.getHunger(hunger) < HungerData.ADEQUATE_THRESHOLD) {
            long gameTime = level().getGameTime();
            long lastAte = HungerData.getLastAteTime(hunger);
            long minEatInterval = HungerData.isEatingMode(hunger) ? 20L
                    : (HungerData.getHunger(hunger) < HungerData.EMERGENCY_THRESHOLD ? 20L : HungerData.MIN_EAT_INTERVAL);
            if ((gameTime - lastAte) >= minEatInterval && !VillagerEatingManager.isEating(self)) {
                hungerChanged |= townstead$tryEatFromInventory(self);
            }
        }

        // --- 7. Mood pressure (every 1200 ticks) ---
        if (tickCount % HungerData.MOOD_CHECK_INTERVAL == 0) {
            int h = HungerData.getHunger(hunger);
            HungerData.HungerState state = HungerData.getState(h);
            float pressure = HungerData.getMoodPressure(state);
            float drift = HungerData.getMoodDrift(hunger) + pressure;
            int moodDelta = 0;
            if (drift >= 1f) {
                moodDelta = (int) Math.floor(drift);
            } else if (drift <= -1f) {
                moodDelta = (int) Math.ceil(drift);
            }
            if (moodDelta != 0) {
                brain.modifyMoodValue(moodDelta);
                drift -= moodDelta;
            }
            HungerData.setMoodDrift(hunger, drift);
        }

        // --- 8. Speed modifier ---
        townstead$updateSpeedModifier(HungerData.getHunger(hunger));

        // --- 9. Persist and sync ---
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
    private boolean townstead$tryEatFromInventory(VillagerEntityMCA self) {
        if (!TownsteadConfig.ENABLE_SELF_INVENTORY_EATING.get()) return false;
        ItemStack food = townstead$findBestFood(self.getInventory());
        if (!food.isEmpty()) {
            return townstead$consumeFood(self, food);
        }
        return false;
    }

    @Unique
    private boolean townstead$consumeFood(VillagerEntityMCA self, ItemStack food) {
        FoodProperties props = food.get(DataComponents.FOOD);
        if (props == null) return false;
        if (!VillagerEatingManager.startEating(self, food)) return false;
        food.shrink(1);
        return false;
    }

    @Unique
    private ItemStack townstead$findBestFood(SimpleContainer inventory) {
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
