package com.aetherianartificer.townstead.mixin.compat.curios;

import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Gives MCA villagers the player's Curios slot set, live. Curios decides an entity's slots from one
 * lookup on its entity manager; every mod that adds a slot (backpacks, rings, charms) targets the
 * player in its data, so answering a villager lookup with the player's map mirrors all of them with
 * no data file to maintain. Runs on both the server and client managers, since both funnel through
 * this method. Only applied when Curios is installed.
 */
@Pseudo
@Mixin(targets = "top.theillusivec4.curios.common.data.CuriosEntityManager", remap = false)
public abstract class CuriosEntitySlotsMixin {

    @Shadow(remap = false)
    public abstract Map<String, ?> getEntitySlots(EntityType<?> type);

    @Inject(method = "getEntitySlots", at = @At("HEAD"), cancellable = true, remap = false)
    private void townstead$mirrorPlayerSlots(EntityType<?> type, CallbackInfoReturnable<Map<String, ?>> cir) {
        if (type == EntityType.PLAYER || !townstead$isVillager(type)) return;
        cir.setReturnValue(getEntitySlots(EntityType.PLAYER));
    }

    /** Identity against MCA's registered villager types; vanilla's base-class query answers Entity for everything. */
    @Unique
    private static boolean townstead$isVillager(EntityType<?> type) {
        //? if >=1.21 {
        return type == net.conczin.mca.registry.EntitiesMCA.MALE_VILLAGER
                || type == net.conczin.mca.registry.EntitiesMCA.FEMALE_VILLAGER;
        //?} else {
        /*return type == net.conczin.mca.entity.EntitiesMCA.MALE_VILLAGER.get()
                || type == net.conczin.mca.entity.EntitiesMCA.FEMALE_VILLAGER.get();
        *///?}
    }
}
