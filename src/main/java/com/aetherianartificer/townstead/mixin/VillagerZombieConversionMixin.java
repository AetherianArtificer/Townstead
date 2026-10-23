package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.villager.ZombifiedVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MCA builds the zombie as a new entity and copies only its own data onto it, so the person's
 * Townstead state is handed over here, once MCA has finished.
 */
@Mixin(VillagerEntityMCA.class)
public abstract class VillagerZombieConversionMixin {

    //? if neoforge {
    @Inject(method = "convertTo", at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "m_21406_", remap = false, at = @At("RETURN"))
    *///?}
    private void townstead$carryIntoZombie(EntityType<?> type, boolean keepInventory, CallbackInfoReturnable<Mob> cir) {
        Mob converted = cir.getReturnValue();
        if (converted instanceof net.conczin.mca.entity.ZombieVillagerEntityMCA) {
            ZombifiedVillagers.zombified((VillagerEntityMCA) (Object) this, converted);
        }
    }
}
