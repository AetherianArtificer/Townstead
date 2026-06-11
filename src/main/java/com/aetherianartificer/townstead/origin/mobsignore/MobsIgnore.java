package com.aetherianartificer.townstead.origin.mobsignore;

import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.origin.ExpressedGenes;
import com.aetherianartificer.townstead.origin.gene.types.MobsIgnoreGeneType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server-side enforcement of {@code mobs_ignore} genes. Resolves the prospective
 * target's expressed genes and, for each, checks the optional mob and relationship
 * conditions. Called from the change-target event to veto a mob locking onto the bearer.
 */
public final class MobsIgnore {

    private MobsIgnore() {}

    /** Whether {@code mob} should ignore {@code target} (the prospective victim). */
    public static boolean shouldIgnore(LivingEntity mob, @Nullable LivingEntity target) {
        if (mob == null || target == null || mob.level().isClientSide) return false;
        List<MobsIgnoreGeneType.Instance> genes =
                ExpressedGenes.instancesOf(target, MobsIgnoreGeneType.Instance.class);
        if (genes.isEmpty()) return false;
        ConditionContext mobCtx = null;
        for (MobsIgnoreGeneType.Instance gene : genes) {
            if (gene.mobCondition() != null) {
                if (mobCtx == null) mobCtx = new ConditionContext(mob);
                if (!gene.mobCondition().test(mobCtx)) continue;
            }
            if (gene.biEntityCondition() != null && !gene.biEntityCondition().test(mob, target)) continue;
            return true;
        }
        return false;
    }
}
