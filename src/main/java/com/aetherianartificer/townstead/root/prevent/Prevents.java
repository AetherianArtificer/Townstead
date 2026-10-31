package com.aetherianartificer.townstead.root.prevent;

import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.root.gene.types.PreventGeneType;
import com.aetherianartificer.townstead.root.gene.types.PreventGeneType.What;
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
     * If one of the entity's genes prevents death, keep it alive at that gene's {@code set_health},
     * run the gene's action and the bearer's {@code when_death_prevented} triggers, and report it
     * prevented. Used by the death-event listener to cancel the killing blow.
     */
    public static boolean tryPreventDeath(LivingEntity entity, @Nullable net.minecraft.world.damagesource.DamageSource source) {
        if (entity == null || entity.level().isClientSide) return false;
        List<PreventGeneType.Instance> genes =
                ExpressedGenes.instancesOf(entity, PreventGeneType.Instance.class);
        if (genes.isEmpty()) return false;
        ConditionContext ctx = null;
        PreventGeneType.Instance saved = null;
        for (PreventGeneType.Instance gene : genes) {
            if (gene.what() != What.DEATH) continue;
            if (gene.condition() != null) {
                if (ctx == null) ctx = new ConditionContext(entity);
                if (!gene.condition().test(ctx)) continue;
            }
            saved = gene;
            break;
        }
        if (saved == null) return false;
        float amount = com.aetherianartificer.townstead.root.trigger.GeneTriggers.lastDamage(entity);
        entity.setHealth(Math.min(entity.getMaxHealth(), Math.max(entity.getHealth(), saved.setHealth())));
        LivingEntity killer = source != null && source.getEntity() instanceof LivingEntity le ? le : null;
        if (saved.action() != null) {
            PreventGeneType.Instance gene = saved;
            com.aetherianartificer.townstead.pheno.event.PhenoEvent.during(
                    new com.aetherianartificer.townstead.pheno.event.PhenoEvent.Frame(amount, source,
                            net.minecraft.world.item.ItemStack.EMPTY),
                    () -> gene.action().run(new com.aetherianartificer.townstead.pheno.action.ActionContext(entity, killer)));
        }
        com.aetherianartificer.townstead.root.trigger.GeneTriggers.onDeathPrevented(entity, source, amount);
        return true;
    }
}
