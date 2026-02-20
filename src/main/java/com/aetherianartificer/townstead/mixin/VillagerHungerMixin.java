package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.hunger.ButcherWorkTask;
import com.aetherianartificer.townstead.hunger.CareForYoungTask;
import com.aetherianartificer.townstead.hunger.CookWorkTask;
import com.aetherianartificer.townstead.hunger.HarvestWorkTask;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.hunger.SeekFoodTask;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerEntityMCA.class)
public abstract class VillagerHungerMixin extends Villager {
    @Unique
    private Brain<?> townstead$lastPatchedBrain;

    private VillagerHungerMixin() {
        super(null, null);
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "makeBrain", at = @At("RETURN"))
    private void townstead$registerSeekFoodOnCreate(Dynamic<?> dynamic, CallbackInfoReturnable<Brain<?>> cir) {
        townstead$addSeekFoodTask((Brain<VillagerEntityMCA>) cir.getReturnValue());
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "refreshBrain", at = @At("TAIL"))
    private void townstead$registerSeekFood(ServerLevel world, CallbackInfo ci) {
        townstead$addSeekFoodTask((Brain<VillagerEntityMCA>) (Brain<?>) getBrain());
    }

    @Unique
    private void townstead$addSeekFoodTask(Brain<VillagerEntityMCA> brain) {
        if (brain == null || brain == townstead$lastPatchedBrain) return;
        brain.addActivity(Activity.CORE,
                ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>>of(
                        Pair.of(70, new HarvestWorkTask()),
                        Pair.of(72, new CookWorkTask()),
                        Pair.of(74, new ButcherWorkTask()),
                        Pair.of(99, new SeekFoodTask()),
                        Pair.of(110, new CareForYoungTask())
                ));
        townstead$lastPatchedBrain = brain;
    }

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
}
