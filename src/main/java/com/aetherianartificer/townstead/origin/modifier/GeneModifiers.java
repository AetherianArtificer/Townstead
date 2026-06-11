package com.aetherianartificer.townstead.origin.modifier;

import com.aetherianartificer.townstead.origin.ExpressedGenes;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.origin.gene.types.ModifierGeneType;
import com.aetherianartificer.townstead.origin.gene.types.ModifierGeneType.Modifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Applies an entity's expressed {@code modifier} genes to a server-resolved scalar
 * (healing received, damage dealt, break speed). Each matching gene combines its
 * value with the running result, gated by its optional condition.
 */
public final class GeneModifiers {

    private GeneModifiers() {}

    /**
     * Scales the bearer's jump velocity by any {@code jump} modifier genes. Called from the
     * jump event, which fires after vanilla sets the jump velocity, so we read and rescale
     * {@code deltaMovement.y}. Cross-version uniform (no jump attribute needed).
     */
    public static void applyJump(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) return;
        net.minecraft.world.phys.Vec3 m = entity.getDeltaMovement();
        float scaled = modify(entity, Modifier.JUMP, (float) m.y);
        if (scaled != (float) m.y) entity.setDeltaMovement(m.x, scaled, m.z);
    }

    public static float modify(LivingEntity entity, Modifier kind, float base) {
        if (entity == null || entity.level().isClientSide) return base;
        List<ModifierGeneType.Instance> genes =
                ExpressedGenes.instancesOf(entity, ModifierGeneType.Instance.class);
        if (genes.isEmpty()) return base;
        float result = base;
        ConditionContext ctx = null;
        for (ModifierGeneType.Instance gene : genes) {
            if (gene.modifier() != kind) continue;
            if (gene.condition() != null) {
                if (ctx == null) ctx = new ConditionContext(entity);
                if (!gene.condition().test(ctx)) continue;
            }
            result = gene.applyTo(result);
        }
        return result;
    }
}
