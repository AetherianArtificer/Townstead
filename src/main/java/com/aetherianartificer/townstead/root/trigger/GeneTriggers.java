package com.aetherianartificer.townstead.root.trigger;

import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.root.gene.types.TriggerGeneType;
import com.aetherianartificer.townstead.root.gene.types.TriggerGeneType.Target;
import com.aetherianartificer.townstead.root.gene.types.TriggerGeneType.Trigger;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server-side dispatcher for {@code trigger} genes. Off the per-version damage event
 * it fires the victim's {@code when_hurt} and the attacker's {@code when_attack}; off
 * the death event it fires the victim's {@code when_death} and the killer's
 * {@code when_kill}. Each gene's action runs on its chosen actor (self or the
 * counterpart), with the other party exposed via {@code ActionContext.other()}.
 */
public final class GeneTriggers {

    private GeneTriggers() {}

    /** Completed transfers only; conditions see the destination. Login and respawn do not call this. */
    public static void onEnterDimension(LivingEntity entity,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> previousDimension) {
        if (entity.level().isClientSide || previousDimension.equals(entity.level().dimension())) return;
        Powers.invalidate(entity);
        fire(entity, Trigger.WHEN_ENTER_DIMENSION, null, null, 0f);
    }

    public static void onDamage(LivingEntity victim, DamageSource source, float amount) {
        if (victim.level().isClientSide) return;
        LivingEntity attacker = source.getEntity() instanceof LivingEntity le ? le : null;
        fire(victim, Trigger.WHEN_HURT, attacker, source, amount);
        if (attacker != null && attacker != victim) fire(attacker, Trigger.WHEN_ATTACK, victim, source, amount);
    }

    public static void onDeath(LivingEntity victim, DamageSource source) {
        if (victim.level().isClientSide) return;
        LivingEntity killer = source.getEntity() instanceof LivingEntity le ? le : null;
        fire(victim, Trigger.WHEN_DEATH, killer, source, 0f);
        if (killer != null && killer != victim) fire(killer, Trigger.WHEN_KILL, victim, source, 0f);
    }

    /** The bearer landed after a fall (Apoli {@code action_on_land}); self-only. */
    public static void onLand(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        fire(entity, Trigger.WHEN_LAND, null, null, 0f);
    }

    /** The bearer woke from a bed (Apoli {@code action_on_wake_up}); self-only. */
    public static void onWakeUp(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        fire(entity, Trigger.WHEN_WAKE_UP, null, null, 0f);
    }

    /** The bearer jumped (Apugli {@code action_on_jump}); self-only. */
    public static void onJump(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        fire(entity, Trigger.WHEN_JUMP, null, null, 0f);
    }

    /** The bearer was struck by lightning (Apugli {@code action_when_lightning_struck}); self-only. */
    public static void onStruckByLightning(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        fire(entity, Trigger.WHEN_STRUCK_BY_LIGHTNING, null, null, 0f);
    }

    /** The bearer's equipment changed (Apugli {@code action_on_equip}); self-only. */
    public static void onEquip(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        fire(entity, Trigger.WHEN_EQUIP, null, null, 0f);
    }

    /** The bearer finished using/consuming an item (Roots {@code action_on_item_use}); self-only. */
    public static void onItemUse(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        onItemUse(entity, net.minecraft.world.item.ItemStack.EMPTY);
    }

    /** The Finish event supplies the original stack, including the last item in a stack. */
    public static void onItemUse(LivingEntity entity, net.minecraft.world.item.ItemStack usedItem) {
        if (entity.level().isClientSide) return;
        fire(entity, Trigger.WHEN_ITEM_USE, null, null, 0f, usedItem);
    }

    /** Fired by the key-press packet handler: run the entity's press triggers bound to {@code key}. */
    public static void firePress(LivingEntity entity, String key) {
        if (entity.level().isClientSide) return;
        List<TriggerGeneType.Instance> triggers = Powers.componentsOf(entity, TriggerGeneType.Instance.class);
        if (triggers.isEmpty()) return;
        ConditionContext ctx = null;
        for (TriggerGeneType.Instance t : triggers) {
            if (t.trigger() != Trigger.PRESS || t.key() == null || !t.key().equalsIgnoreCase(key)) continue;
            if (t.condition() != null) {
                if (ctx == null) ctx = new ConditionContext(entity);
                if (!t.condition().test(ctx)) continue;
            }
            t.action().run(new ActionContext(entity));
        }
    }

    private static void fire(LivingEntity bearer, Trigger trigger, @Nullable LivingEntity other,
                             @Nullable DamageSource source, float amount) {
        fire(bearer, trigger, other, source, amount, net.minecraft.world.item.ItemStack.EMPTY);
    }

    private static void fire(LivingEntity bearer, Trigger trigger, @Nullable LivingEntity other,
                             @Nullable DamageSource source, float amount, net.minecraft.world.item.ItemStack usedItem) {
        List<TriggerGeneType.Instance> triggers = Powers.componentsOf(bearer, TriggerGeneType.Instance.class);
        if (triggers.isEmpty()) return;
        ConditionContext ctx = null;
        for (TriggerGeneType.Instance t : triggers) {
            if (t.trigger() != trigger) continue;
            if (t.itemCondition() != null && !t.itemCondition().test(bearer.level(), usedItem)) continue;
            LivingEntity primary = t.target() == Target.OTHER ? other : bearer;
            if (primary == null) continue;
            // A damage_condition can only hold where a damage event is in play (hurt/attack/kill/death).
            if (t.damageCondition() != null && (source == null || !t.damageCondition().test(source, amount))) continue;
            if (t.condition() != null) {
                if (ctx == null) ctx = new ConditionContext(bearer);
                if (!t.condition().test(ctx)) continue;
            }
            LivingEntity counterpart = primary == bearer ? other : bearer;
            t.action().run(new ActionContext(primary, counterpart));
        }
    }
}
