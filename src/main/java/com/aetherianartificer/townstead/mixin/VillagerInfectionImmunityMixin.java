package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.ExpressedGenes;
import com.aetherianartificer.townstead.origin.gene.types.InfectionImmunityGeneType;
import net.conczin.mca.entity.VillagerEntityMCA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A race with the {@code infection_immune} gene can't be zombie-infected (e.g. skeletownies — a
 * skeleton turning into a zombie makes no sense). MCA funnels every infection path (the zombie-bite
 * {@code setInfected}, the per-tick progression that converts at 1.0) through
 * {@code setInfectionProgress}, so blocking any positive set there stops the infection from ever
 * starting or advancing. Gene-gated rather than tied to {@code entity_group} undead: an undead
 * non-humanoid body (a skeleton horse) is still "undead" for combat but must not ride MCA's humanoid
 * zombie-villager conversion. Clearing to 0 (a cure) is always allowed; server-side only.
 */
@Mixin(VillagerEntityMCA.class)
public abstract class VillagerInfectionImmunityMixin {

    @Inject(method = "setInfectionProgress", at = @At("HEAD"), cancellable = true, remap = false)
    private void townstead$blockImmuneInfection(float progress, CallbackInfo ci) {
        if (progress <= 0f) return;
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        if (!self.level().isClientSide
                && !ExpressedGenes.instancesOf(self, InfectionImmunityGeneType.Instance.class).isEmpty()) {
            ci.cancel();
        }
    }
}
