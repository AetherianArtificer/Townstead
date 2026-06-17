package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.reproduction.Fertility;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks reproduction for a sterile parent (a {@code townstead_origins:fertility} gene of 0, e.g.
 * skeletons). MCA reproduces two ways and both funnel through {@code Pregnancy}: {@code procreate}
 * makes a baby item (the player/villager procreate path), and {@code tryStartGestation} starts a real
 * pregnancy (the gestation path). Gating both at their head, on either prospective parent, guarantees
 * no offspring. Marriage and pairing are untouched; only conception is blocked.
 *
 * <p>{@code remap=false}: the target lives in MCA, whose names are stable across both branches (same
 * as {@code PregnancyInheritanceMixin}).</p>
 */
@Mixin(value = net.conczin.mca.entity.ai.Pregnancy.class, remap = false)
public abstract class FertilityPregnancyMixin {

    @Shadow
    private VillagerEntityMCA mother;

    @Inject(method = "tryStartGestation", at = @At("HEAD"), cancellable = true)
    private void townstead$gateGestation(CallbackInfoReturnable<Boolean> cir) {
        if (!Fertility.canBreed(mother, partner())) cir.setReturnValue(false);
    }

    @Inject(method = "procreate", at = @At("HEAD"), cancellable = true)
    private void townstead$gateProcreate(Entity spouse, CallbackInfo ci) {
        LivingEntity other = spouse instanceof LivingEntity living ? living : null;
        if (!Fertility.isFertile(mother) || (other != null && !Fertility.isFertile(other))) {
            ci.cancel();
        }
    }

    private VillagerEntityMCA partner() {
        if (mother == null) return null;
        return mother.getRelationships().getPartner()
                .filter(VillagerEntityMCA.class::isInstance)
                .map(VillagerEntityMCA.class::cast)
                .orElse(null);
    }
}
