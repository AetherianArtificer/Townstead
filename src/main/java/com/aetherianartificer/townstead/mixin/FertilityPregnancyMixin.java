package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.reproduction.DirectBirth;
import com.aetherianartificer.townstead.root.reproduction.Fertility;
import com.aetherianartificer.townstead.root.reproduction.SpeciesBreeding;
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
 * Blocks reproduction for a sterile parent (a {@code townstead_roots:fertility} gene of 0, e.g.
 * skeletons). MCA reproduces two ways and both funnel through {@code Pregnancy}: {@code procreate}
 * makes a baby item (the player/villager procreate path), and {@code tryStartGestation} starts a real
 * pregnancy (the gestation path). Gating both at their head, on either prospective parent, guarantees
 * no offspring. Marriage and pairing are untouched; only conception is blocked.
 *
 * <p>The same two chokepoints also enforce the species barrier: two parents of different effective
 * species can't conceive (see {@link SpeciesBreeding}). On the player-facing {@code procreate} path the
 * refused player is told why; the autonomous {@code tryStartGestation} path blocks silently.</p>
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
        VillagerEntityMCA partner = partner();
        if (!Fertility.canBreed(mother, partner)) {
            cir.setReturnValue(false);
            return;
        }
        if (partner != null && !SpeciesBreeding.sameSpecies(mother, partner)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "procreate", at = @At("HEAD"), cancellable = true)
    private void townstead$gateProcreate(Entity spouse, CallbackInfo ci) {
        LivingEntity other = spouse instanceof LivingEntity living ? living : null;
        if (!Fertility.isFertile(mother) || (other != null && !Fertility.isFertile(other))
                || !Fertility.canBear(mother) || (other != null && !Fertility.canBear(other))) {
            ci.cancel();
            return;
        }
        if (other != null && !SpeciesBreeding.sameSpecies(mother, other)) {
            ci.cancel();
            SpeciesBreeding.notifyIfPlayer(spouse);
            return;
        }
        // A fertile, same-species pair. Non-overworlder species bear young directly
        // rather than handing the player a (human) baby item.
        if (DirectBirth.bypassesBabyItem(mother)) {
            DirectBirth.spawnOffspring(mother, spouse);
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
