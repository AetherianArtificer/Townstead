package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.reproduction.DirectBirth;
import com.aetherianartificer.townstead.root.reproduction.GestationLength;
import com.aetherianartificer.townstead.root.reproduction.LitterSize;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Pregnancy;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.world.entity.MobSpawnType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Egg-laying reproduction scalars on the mother's pregnancy.
 *
 * <p>LITTER: MCA spawns ONE child at the end of gestation ({@code tick} → {@code createChild} +
 * {@code spawnEntity}). At {@code createChild}'s RETURN (the child is built + inherited, not yet spawned)
 * we resolve the mother's {@code litter_size} and spawn {@code litter_size - 1} extra children, each made
 * via the same {@code createChild} (so each gets fresh genetics + Townstead inheritance) and spawned at the
 * mother. A re-entrancy guard skips the litter logic on those nested {@code createChild} calls.</p>
 *
 * <p>GESTATION: MCA grows {@code babyAge} by a literal {@code 60} per {@code tick}; we scale that by
 * {@code 1 / gestation_length} so a longer multiplier = slower growth = longer pregnancy (per-mother).</p>
 *
 * <p>{@code remap=false}: MCA's {@code Pregnancy} method/field names are stable across both stonecutter
 * branches (the MC calls {@code setPos}/{@code level}/{@code MobSpawnType} are remapped by the build).</p>
 */
@Mixin(value = Pregnancy.class, remap = false)
public abstract class LitterGestationMixin {

    @Shadow private VillagerEntityMCA mother;

    private static final ThreadLocal<Boolean> townstead$litterGuard = ThreadLocal.withInitial(() -> false);

    @Inject(method = "createChild", at = @At("RETURN"))
    private void townstead$litter(Gender gender, VillagerEntityMCA partner,
                                  CallbackInfoReturnable<VillagerEntityMCA> cir) {
        VillagerEntityMCA child = cir.getReturnValue();
        if (child == null || mother == null || partner == null || child.level().isClientSide) return;
        if (townstead$litterGuard.get()) return;
        // The direct-birth path (non-overworlders) spawns the whole clutch itself.
        if (DirectBirth.spawning()) return;
        int litter = LitterSize.roll(mother, mother.getRandom());
        if (litter <= 1) return;
        townstead$litterGuard.set(true);
        try {
            Pregnancy self = (Pregnancy) (Object) this;
            for (int i = 1; i < litter; i++) {
                VillagerEntityMCA extra = self.createChild(gender, partner);
                if (extra == null) continue;
                DirectBirth.scatterAround(extra, mother, mother.getRandom());
                WorldUtils.spawnEntity(mother.level(), extra, MobSpawnType.BREEDING);
            }
        } finally {
            townstead$litterGuard.set(false);
        }
    }

    // A chaining modifier: a second @ModifyConstant on the same constant from another mod fails to apply.
    @ModifyExpressionValue(method = "tick", at = @At(value = "CONSTANT", args = "intValue=60"))
    private int townstead$gestationRate(int original) {
        return Math.max(1, Math.round(original / GestationLength.forMother(mother)));
    }
}
