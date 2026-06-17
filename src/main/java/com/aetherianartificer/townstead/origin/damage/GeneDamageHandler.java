package com.aetherianartificer.townstead.origin.damage;

import com.aetherianartificer.townstead.origin.ExpressedGenes;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.origin.gene.types.DamageModifierGeneType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Scales incoming damage by a victim's expressed {@code damage_modifier} genes.
 * Wired to the per-version damage event (NeoForge {@code LivingIncomingDamageEvent},
 * Forge {@code LivingHurtEvent}); also the home of {@code fire_immunity}'s damage
 * cancel.
 */
public final class GeneDamageHandler {

    private GeneDamageHandler() {}

    /** The amount after applying every matching damage-modifier gene (0 = fully blocked). */
    public static float modify(LivingEntity victim, DamageSource source, float amount) {
        if (victim.level().isClientSide) return amount;
        float result = amount;
        List<DamageModifierGeneType.Instance> genes =
                ExpressedGenes.instancesOf(victim, DamageModifierGeneType.Instance.class);
        if (!genes.isEmpty()) {
            ConditionContext ctx = new ConditionContext(victim);
            for (DamageModifierGeneType.Instance gene : genes) {
                if ((gene.damageTag() != null || gene.damageType() != null) && !matches(source, gene)) continue;
                if (gene.damageCondition() != null && !gene.damageCondition().test(source, amount)) continue;
                if (gene.condition() != null && !gene.condition().test(ctx)) continue;
                result *= gene.modifier();
            }
        }
        //? if >=1.21 {
        // 1.20.1 gets Smite/Bane via the getMobType mixin; 1.21.1 has no per-entity mob
        // type, so add the enchantment bonus here for entity_group undead/arthropod victims.
        result += com.aetherianartificer.townstead.origin.damage.EntityGroupCombat.enchantBonus(victim, source);
        //?}
        // The attacker's modify_damage_dealt scales what it deals (resolved here, on the
        // same victim-side event, so it stays server-authoritative). Projectile hits also fold
        // in modify_projectile_damage (the shooter is source.getEntity(), the arrow is direct).
        if (source.getEntity() instanceof LivingEntity attacker && attacker != victim) {
            result = com.aetherianartificer.townstead.origin.hook.PhenoHooks.damageDealt(attacker, result);
            if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile) {
                result = com.aetherianartificer.townstead.origin.hook.PhenoHooks.projectileDamage(attacker, result);
            }
        }
        return Math.max(0f, result);
    }

    private static boolean matches(DamageSource source, DamageModifierGeneType.Instance gene) {
        if (gene.damageTag() != null && source.is(gene.damageTag())) return true;
        if (gene.damageType() != null && source.is(ResourceKey.create(Registries.DAMAGE_TYPE, gene.damageType()))) {
            return true;
        }
        return false;
    }
}
