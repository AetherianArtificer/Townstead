package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.fatigue.SeekBedWhenFatiguedTask;
import com.aetherianartificer.townstead.hunger.ButcherWorkTask;
import com.aetherianartificer.townstead.hunger.FishermanWorkTask;
import com.aetherianartificer.townstead.shift.ShiftScheduleApplier;
import com.aetherianartificer.townstead.hunger.CareForYoungTask;
import com.aetherianartificer.townstead.compat.farmersdelight.BaristaWorkTask;
import com.aetherianartificer.townstead.compat.farmersdelight.CookWorkTask;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.hunger.HarvestWorkTask;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.hunger.SeekFoodTask;
import com.aetherianartificer.townstead.thirst.HydrateYoungTask;
import com.aetherianartificer.townstead.thirst.SeekDrinkTask;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

@Mixin(VillagerEntityMCA.class)
public abstract class VillagerHungerMixin extends Villager {
    @Unique
    private Brain<?> townstead$lastPatchedBrain;

    private VillagerHungerMixin() {
        super(null, null);
    }

    @SuppressWarnings("unchecked")
    //? if neoforge {
    @Inject(method = "makeBrain", at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "m_8075_", remap = false, at = @At("RETURN"))
    *///?}
    private void townstead$registerSeekFoodOnCreate(Dynamic<?> dynamic, CallbackInfoReturnable<Brain<?>> cir) {
        townstead$addSeekFoodTask((Brain<VillagerEntityMCA>) cir.getReturnValue());
    }

    @SuppressWarnings("unchecked")
    //? if neoforge {
    @Inject(method = "refreshBrain", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_35483_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$registerSeekFood(ServerLevel world, CallbackInfo ci) {
        townstead$addSeekFoodTask((Brain<VillagerEntityMCA>) (Brain<?>) getBrain());
    }

    @Unique
    private void townstead$addSeekFoodTask(Brain<VillagerEntityMCA> brain) {
        if (brain == null || brain == townstead$lastPatchedBrain) return;
        // Work behaviors go in Activity.WORK so they set WALK_TARGET after MCA's
        // built-in work behaviors, preventing job-site pathing from overriding ours.
        brain.addActivity(Activity.WORK,
                ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>>of(
                        Pair.of(70, new HarvestWorkTask()),
                        Pair.of(71, new FishermanWorkTask()),
                        Pair.of(72, new CookWorkTask()),
                        Pair.of(72, new BaristaWorkTask()),
                        Pair.of(73, new com.aetherianartificer.townstead.compat.butchery.SlaughterWorkTask()),
                        Pair.of(73, new com.aetherianartificer.townstead.compat.butchery.CarcassWorkTask()),
                        Pair.of(74, new ButcherWorkTask())
                ));
        // Non-work behaviors stay in CORE so they tick regardless of schedule activity.
        ArrayList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> coreBehaviors = new ArrayList<>();
        if (ThirstBridgeResolver.isActive()) {
            coreBehaviors.add(Pair.of(98, new SeekDrinkTask()));
        }
        coreBehaviors.add(Pair.of(65, new SeekBedWhenFatiguedTask()));
        coreBehaviors.add(Pair.of(99, new SeekFoodTask()));
        coreBehaviors.add(Pair.of(110, new CareForYoungTask()));
        if (ThirstBridgeResolver.isActive()) {
            coreBehaviors.add(Pair.of(111, new HydrateYoungTask()));
        }
        brain.addActivity(Activity.CORE, ImmutableList.copyOf(coreBehaviors));
        townstead$lastPatchedBrain = brain;

        // Prevent villagers from pathfinding onto direct fire-damage blocks.
        // Keep vanilla danger handling intact so normal home/interior navigation still works.
        // Guard: pathfindingMalus map is null during makeBrain (entity not fully constructed).
        try {
            //? if >=1.21 {
            setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.DAMAGE_FIRE, -1.0f);
            //?} else {
            /*setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DAMAGE_FIRE, -1.0f);
            *///?}
        } catch (NullPointerException ignored) {
            // Entity still constructing — will be set on refreshBrain
        }

        // Apply custom shift schedule if one has been assigned
        VillagerEntityMCA self = (VillagerEntityMCA)(Object)this;
        if (!self.level().isClientSide) {
            ShiftScheduleApplier.apply(self);
        }
    }

    //? if neoforge {
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_7380_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$writeEditorVitals(CompoundTag nbt, CallbackInfo ci) {
        VillagerEntityMCA self = (VillagerEntityMCA)(Object)this;

        // Skip on client: MCA's syncVillagerData() calls villager.save(tag) on the
        // client entity, whose data attachments have defaults (not real values).
        // Writing here would overwrite any editor changes the player made.
        if (self.level().isClientSide) return;

        //? if neoforge {
        CompoundTag hunger = self.getData(Townstead.HUNGER_DATA);
        CompoundTag fatigue = self.getData(Townstead.FATIGUE_DATA);
        //?} else {
        /*CompoundTag hunger = self.getPersistentData().getCompound("townstead_hunger");
        CompoundTag fatigue = self.getPersistentData().getCompound("townstead_fatigue");
        *///?}
        nbt.putInt(HungerData.EDITOR_KEY_HUNGER, HungerData.getHunger(hunger));
        nbt.putFloat(HungerData.EDITOR_KEY_SATURATION, HungerData.getSaturation(hunger));
        nbt.putFloat(HungerData.EDITOR_KEY_EXHAUSTION, HungerData.getExhaustion(hunger));
        nbt.putInt(FatigueData.EDITOR_KEY_FATIGUE, FatigueData.getFatigue(fatigue));

        if (ThirstBridgeResolver.isActive()) {
            //? if neoforge {
            CompoundTag thirst = self.getData(Townstead.THIRST_DATA);
            //?} else {
            /*CompoundTag thirst = self.getPersistentData().getCompound("townstead_thirst");
            *///?}
            nbt.putInt(ThirstData.EDITOR_KEY_THIRST, ThirstData.getThirst(thirst));
            nbt.putInt(ThirstData.EDITOR_KEY_QUENCHED, ThirstData.getQuenched(thirst));
            nbt.putFloat(ThirstData.EDITOR_KEY_EXHAUSTION, ThirstData.getExhaustion(thirst));
        }
    }

    //? if neoforge {
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_7378_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$readEditorHunger(CompoundTag nbt, CallbackInfo ci) {
        VillagerEntityMCA self = (VillagerEntityMCA)(Object)this;
        boolean hasHunger = nbt.contains(HungerData.EDITOR_KEY_HUNGER);
        boolean bridgeActive = ThirstBridgeResolver.isActive();
        boolean nbtHasThirst = nbt.contains(ThirstData.EDITOR_KEY_THIRST);
        boolean hasThirst = bridgeActive && nbtHasThirst;
        boolean hasFatigue = nbt.contains(FatigueData.EDITOR_KEY_FATIGUE);
        if (!hasHunger && !hasThirst && !hasFatigue) return;

        if (hasHunger) {
            //? if neoforge {
            CompoundTag hunger = self.getData(Townstead.HUNGER_DATA);
            //?} else {
            /*CompoundTag hunger = self.getPersistentData().getCompound("townstead_hunger");
            *///?}
            HungerData.setHunger(hunger, nbt.getInt(HungerData.EDITOR_KEY_HUNGER));
            HungerData.setSaturation(hunger, nbt.getFloat(HungerData.EDITOR_KEY_SATURATION));
            HungerData.setExhaustion(hunger, nbt.getFloat(HungerData.EDITOR_KEY_EXHAUSTION));
            //? if neoforge {
            self.setData(Townstead.HUNGER_DATA, hunger);
            //?} else {
            /*self.getPersistentData().put("townstead_hunger", hunger);
            *///?}
        }

        if (hasThirst) {
            int newThirst = nbt.getInt(ThirstData.EDITOR_KEY_THIRST);
            //? if neoforge {
            CompoundTag thirst = self.getData(Townstead.THIRST_DATA);
            //?} else {
            /*CompoundTag thirst = self.getPersistentData().getCompound("townstead_thirst");
            *///?}
            ThirstData.setThirst(thirst, newThirst);
            ThirstData.setQuenched(thirst, nbt.getInt(ThirstData.EDITOR_KEY_QUENCHED));
            ThirstData.setExhaustion(thirst, nbt.getFloat(ThirstData.EDITOR_KEY_EXHAUSTION));
            //? if neoforge {
            self.setData(Townstead.THIRST_DATA, thirst);
            //?} else {
            /*self.getPersistentData().put("townstead_thirst", thirst);
            *///?}
            if (!self.level().isClientSide) {
                //? if neoforge {
                PacketDistributor.sendToPlayersTrackingEntity(self, Townstead.townstead$thirstSync(self, thirst));
                //?} else if forge {
                /*TownsteadNetwork.sendToTrackingEntity(self, Townstead.townstead$thirstSync(self, thirst));
                *///?}
            }
        }

        if (hasFatigue) {
            int newFatigue = nbt.getInt(FatigueData.EDITOR_KEY_FATIGUE);
            //? if neoforge {
            CompoundTag fatigue = self.getData(Townstead.FATIGUE_DATA);
            //?} else {
            /*CompoundTag fatigue = self.getPersistentData().getCompound("townstead_fatigue");
            *///?}
            FatigueData.setFatigue(fatigue, newFatigue);
            // Clear collapse/gate if below thresholds
            if (newFatigue < FatigueData.COLLAPSE_THRESHOLD) {
                FatigueData.setCollapsed(fatigue, false);
            }
            if (newFatigue < FatigueData.RECOVERY_GATE) {
                FatigueData.setGated(fatigue, false);
            }
            //? if neoforge {
            self.setData(Townstead.FATIGUE_DATA, fatigue);
            //?} else {
            /*self.getPersistentData().put("townstead_fatigue", fatigue);
            *///?}
            if (!self.level().isClientSide) {
                //? if neoforge {
                PacketDistributor.sendToPlayersTrackingEntity(self, Townstead.townstead$fatigueSync(self, fatigue));
                //?} else if forge {
                /*TownsteadNetwork.sendToTrackingEntity(self, Townstead.townstead$fatigueSync(self, fatigue));
                *///?}
            }
        }
    }
}
