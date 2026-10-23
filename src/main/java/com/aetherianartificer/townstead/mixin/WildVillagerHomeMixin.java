package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.replace.MobReplacer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A wild villager has no home: MCA then gives it the wanderer's brain and never enrolls it in a
 * village it walks into. Settling clears the tag and MCA's own home seeking takes over. Anyone in a
 * state that keeps them out of villages (a newcomer, a visitor) has no home the same way.
 */
@Mixin(value = VillagerEntityMCA.class, remap = false)
public abstract class WildVillagerHomeMixin {
    @Inject(method = "requiresHome", at = @At("RETURN"), cancellable = true, require = 1)
    private void townstead$wildHasNoHome(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        if (MobReplacer.isWild(self)) {
            cir.setReturnValue(false);
            return;
        }
        // The first brain is built inside the constructor (no brain yet, nothing loaded), and the
        // client never decides residency: states are read only on a whole server villager.
        if (self.level().isClientSide || self.getBrain() == null) return;
        if (com.aetherianartificer.townstead.pheno.state.EntityStates.nonResident(self)) cir.setReturnValue(false);
    }

    // The brain is built before the save is read, so a wild villager rebuilds it once its tag is known.
    //? if neoforge {
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"), require = 1)
    //?} else {
    /*@Inject(method = "m_7378_", at = @At("TAIL"), require = 1)
    *///?}
    private void townstead$wildBrainAfterLoad(CompoundTag nbt, CallbackInfo ci) {
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        //? if neoforge {
        CompoundTag saved = nbt.getCompound("NeoForgeData");
        //?} else {
        /*CompoundTag saved = nbt.getCompound("ForgeData");
        *///?}
        if (!saved.contains(MobReplacer.WILD) || !(self.level() instanceof ServerLevel level)) return;
        self.getPersistentData().putString(MobReplacer.WILD, saved.getString(MobReplacer.WILD));
        self.refreshBrain(level);
    }
}
