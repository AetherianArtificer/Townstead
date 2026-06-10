package com.aetherianartificer.townstead.origin.trigger;

import com.aetherianartificer.townstead.habitus.power.Powers;
import com.aetherianartificer.townstead.habitus.action.ActionContext;
import com.aetherianartificer.townstead.habitus.condition.ConditionContext;
import com.aetherianartificer.townstead.origin.gene.types.TriggerGeneType;
import com.aetherianartificer.townstead.origin.gene.types.TriggerGeneType.Target;
import com.aetherianartificer.townstead.origin.gene.types.TriggerGeneType.Trigger;
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

    private static void fire(LivingEntity bearer, Trigger trigger, @Nullable LivingEntity other,
                             @Nullable DamageSource source, float amount) {
        List<TriggerGeneType.Instance> triggers = Powers.componentsOf(bearer, TriggerGeneType.Instance.class);
        if (triggers.isEmpty()) return;
        ConditionContext ctx = null;
        for (TriggerGeneType.Instance t : triggers) {
            if (t.trigger() != trigger) continue;
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
