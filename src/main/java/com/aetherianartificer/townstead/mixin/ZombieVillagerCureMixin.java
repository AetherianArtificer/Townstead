package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.villager.ZombifiedVillagers;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A zombie villager who was someone: their name keeps its family name, and a cure brings back the
 * person they were rather than a newcomer. The villager joins the world before MCA copies them over,
 * so arrival waits until the copy is done.
 */
@Mixin(ZombieVillagerEntityMCA.class)
public abstract class ZombieVillagerCureMixin {

    //? if neoforge {
    @Inject(method = "convertTo", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "m_21406_", remap = false, at = @At("HEAD"))
    *///?}
    private void townstead$beginCure(EntityType<?> type, boolean keepInventory, CallbackInfoReturnable<Mob> cir) {
        ZombifiedVillagers.curing((ZombieVillagerEntityMCA) (Object) this);
    }

    //? if neoforge {
    @Inject(method = "convertTo", at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "m_21406_", remap = false, at = @At("RETURN"))
    *///?}
    private void townstead$finishCure(EntityType<?> type, boolean keepInventory, CallbackInfoReturnable<Mob> cir) {
        ZombifiedVillagers.cured((ZombieVillagerEntityMCA) (Object) this, cir.getReturnValue());
    }

    // MCA's 1.20.1 zombie doesn't declare getDisplayName, so there the nameplate alone composes it.
    //? if neoforge {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    //?} else {
    /*@Inject(method = "m_5446_", remap = false, at = @At("RETURN"), cancellable = true, require = 0)
    *///?}
    private void townstead$composeDisplayName(CallbackInfoReturnable<Component> cir) {
        Component composed = com.aetherianartificer.townstead.naming.DisplayNames.ofZombie(
                (ZombieVillagerEntityMCA) (Object) this, cir.getReturnValue());
        if (composed != cir.getReturnValue()) cir.setReturnValue(composed);
    }
}
