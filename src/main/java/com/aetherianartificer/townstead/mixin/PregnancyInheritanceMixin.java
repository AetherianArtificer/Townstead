package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.Heredity;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies Townstead's diploid inheritance to a freshly-bred child. MCA's
 * {@code Pregnancy.createChild} already combines the parents' float genetics and
 * traits; at its return the child entity is fully built but not yet spawned, so we
 * draw the child's heritage and per-locus alleles from both parents here. The
 * later FinalizeSpawn / backfill path detects the inherited state and leaves it
 * intact (it only rolls stage durations).
 *
 * <p>{@code remap=false}: the target lives in MCA, whose method/field names are
 * stable across both stonecutter branches.</p>
 */
@Mixin(value = net.conczin.mca.entity.ai.Pregnancy.class, remap = false)
public abstract class PregnancyInheritanceMixin {

    @Shadow
    private VillagerEntityMCA mother;

    @Inject(method = "createChild", at = @At("RETURN"))
    private void townstead$inheritGenetics(Gender gender, VillagerEntityMCA partner,
                                           CallbackInfoReturnable<VillagerEntityMCA> cir) {
        VillagerEntityMCA child = cir.getReturnValue();
        if (child == null || mother == null || partner == null) return;
        if (child.level().isClientSide) return;
        Heredity.inherit(
                TownsteadVillagers.get(child).life(),
                Heredity.parentOf(mother, child.getRandom()),
                Heredity.parentOf(partner, child.getRandom()),
                child.getRandom());
    }
}
