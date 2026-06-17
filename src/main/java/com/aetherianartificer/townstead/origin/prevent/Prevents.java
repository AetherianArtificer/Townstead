package com.aetherianartificer.townstead.origin.prevent;

import com.aetherianartificer.townstead.origin.ExpressedGenes;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.origin.gene.types.PreventGeneType;
import com.aetherianartificer.townstead.origin.gene.types.PreventGeneType.What;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server-side enforcement of {@code prevent} genes. Resolves an entity's expressed
 * prevent genes for a given {@link What}, honoring each gene's condition and (for
 * item-scoped prevents) item filter. Called from the death / sleep / item-use events
 * and the collision mixin.
 */
public final class Prevents {

    private Prevents() {}

    /** Whether any expressed gene prevents {@code what} on this entity. */
    public static boolean prevents(LivingEntity entity, What what) {
        return prevents(entity, what, null);
    }

    public static boolean prevents(LivingEntity entity, What what, @Nullable ItemStack stack) {
        if (entity == null) return false;
        List<PreventGeneType.Instance> genes =
                ExpressedGenes.instancesOf(entity, PreventGeneType.Instance.class);
        if (genes.isEmpty()) return false;
        ConditionContext ctx = null;
        for (PreventGeneType.Instance gene : genes) {
            if (gene.what() != what) continue;
            if (stack != null && !gene.matchesItem(stack)) continue;
            if (gene.condition() != null) {
                if (ctx == null) ctx = new ConditionContext(entity);
                if (!gene.condition().test(ctx)) continue;
            }
            return true;
        }
        return false;
    }

    /**
     * If the entity's gene prevents death, keep it alive at 1 health and report it
     * prevented. Used by the death-event listener to cancel the killing blow.
     */
    public static boolean tryPreventDeath(LivingEntity entity) {
        if (entity.level().isClientSide || !prevents(entity, What.DEATH)) return false;
        if (entity.getHealth() <= 0f) entity.setHealth(1f);
        return true;
    }
}
