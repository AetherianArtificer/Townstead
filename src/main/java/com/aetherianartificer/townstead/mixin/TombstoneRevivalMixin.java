package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.thirst.ThirstData;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

//? if neoforge {
import net.conczin.mca.block.TombstoneBlock;
//?} else {
/*import forge.net.mca.block.TombstoneBlock;
*///?}

/**
 * When a villager is resurrected from a tombstone, reset their hunger,
 * thirst, and energy to midpoints so they don't immediately suffer.
 */
@Mixin(TombstoneBlock.Data.class)
public class TombstoneRevivalMixin {

    @Inject(method = "createEntity", at = @At("RETURN"), remap = false)
    private void townstead$resetNeedsOnRevival(Level level, boolean forRendering,
                                                CallbackInfoReturnable<Optional<Entity>> cir) {
        if (forRendering) return;
        Optional<Entity> result = cir.getReturnValue();
        if (result.isEmpty()) return;
        Entity entity = result.get();
        if (!(entity instanceof VillagerEntityMCA villager)) return;

        // Reset hunger to midpoint
        //? if neoforge {
        CompoundTag hunger = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag hunger = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        HungerData.setHunger(hunger, HungerData.MAX_HUNGER / 2);
        HungerData.setSaturation(hunger, HungerData.MAX_SATURATION / 2);
        HungerData.setExhaustion(hunger, 0f);
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, hunger);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", hunger);
        *///?}

        // Reset thirst if active
        if (ThirstBridgeResolver.isActive()) {
            //? if neoforge {
            CompoundTag thirst = villager.getData(Townstead.THIRST_DATA);
            //?} else {
            /*CompoundTag thirst = villager.getPersistentData().getCompound("townstead_thirst");
            *///?}
            ThirstData.setThirst(thirst, ThirstData.MAX_THIRST / 2);
            ThirstData.setQuenched(thirst, ThirstData.MAX_QUENCHED / 4);
            ThirstData.setExhaustion(thirst, 0f);
            ThirstData.setDamageTimer(thirst, 0);
            //? if neoforge {
            villager.setData(Townstead.THIRST_DATA, thirst);
            //?} else {
            /*villager.getPersistentData().put("townstead_thirst", thirst);
            *///?}
        }

        // Reset fatigue
        if (TownsteadConfig.isVillagerFatigueEnabled()) {
            //? if neoforge {
            CompoundTag fatigue = villager.getData(Townstead.FATIGUE_DATA);
            //?} else {
            /*CompoundTag fatigue = villager.getPersistentData().getCompound("townstead_fatigue");
            *///?}
            FatigueData.setFatigue(fatigue, FatigueData.MAX_FATIGUE / 2);
            FatigueData.setCollapsed(fatigue, false);
            FatigueData.setGated(fatigue, false);
            //? if neoforge {
            villager.setData(Townstead.FATIGUE_DATA, fatigue);
            //?} else {
            /*villager.getPersistentData().put("townstead_fatigue", fatigue);
            *///?}
        }
    }
}
