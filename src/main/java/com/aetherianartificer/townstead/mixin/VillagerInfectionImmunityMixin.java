package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.root.gene.types.InfectionImmunityGeneType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A race with the {@code infection_immune} gene can't be zombie-infected (e.g. skeletownies — a
 * skeleton turning into a zombie makes no sense). MCA funnels every infection path (the zombie-bite
 * {@code setInfected}, the per-tick progression that converts at 1.0) through
 * {@code setInfectionProgress}, so blocking any positive set there stops the infection from ever
 * starting or advancing. Gene-gated rather than tied to {@code entity_group} undead: an undead
 * non-humanoid body (a skeleton horse) is still "undead" for combat but must not ride MCA's humanoid
 * zombie-villager conversion. Clearing to 0 (a cure) is always allowed; server-side only. A direct
 * {@code convertTo} a zombie villager is refused under the same rule.
 */
@Mixin(VillagerEntityMCA.class)
public abstract class VillagerInfectionImmunityMixin {

    @Inject(method = "setInfectionProgress", at = @At("HEAD"), cancellable = true, remap = false)
    private void townstead$blockImmuneInfection(float progress, CallbackInfo ci) {
        if (progress <= 0f) return;
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        if (self.level().isClientSide) return;
        if (townstead$immune(self)) ci.cancel();
    }

    // Anything that turns a villager straight into a zombie (a pheno action, another mod) skips the
    // infection, so the conversion itself is held to the same rule.
    //? if neoforge {
    @Inject(method = "convertTo", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_21406_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$blockImmuneConversion(EntityType<?> type, boolean keepInventory, CallbackInfoReturnable<Mob> cir) {
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        if (type != EntityType.ZOMBIE_VILLAGER || self.level().isClientSide) return;
        if (townstead$immune(self)) cir.setReturnValue(null);
    }

    @Unique
    private static boolean townstead$immune(VillagerEntityMCA self) {
        // A Persona protected from dying (downed instead) is not lost to a zombie bite either.
        return !ExpressedGenes.instancesOf(self, InfectionImmunityGeneType.Instance.class).isEmpty()
                || (com.aetherianartificer.townstead.persona.PersonaService.definition(self) != null
                    && com.aetherianartificer.townstead.downed.DownedService.covers(self));
    }
}
