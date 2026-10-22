package com.aetherianartificer.townstead.root.locomotion;

import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.root.ability.Ability;
import com.aetherianartificer.townstead.root.gene.types.AbilityGeneType;
import com.aetherianartificer.townstead.root.gene.types.ActiveAbilityGeneType;
import com.aetherianartificer.townstead.root.gene.types.TriggerGeneType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A flier's resolved movement: which gene grants it, the style, and the cruise height.
 * Wings win over the {@code hover} ability when a bearer has both.
 *
 * @param power    the gene granting flight
 * @param style    explicit {@code style}, else inferred
 * @param explicit whether the gene named its style
 */
public record FlightProfile(Power power, Ability ability, FlightStyle style, double cruiseHeight, boolean explicit) {

    /** The bearer's flight profile, or null for a non-flier. Scans active genes; call at decision points, not every tick. */
    @Nullable
    public static FlightProfile of(LivingEntity entity) {
        Power hover = null;
        for (Power power : Powers.active(entity)) {
            if (!(power.component() instanceof AbilityGeneType.Instance ability)) continue;
            if (ability.ability() == Ability.ELYTRA_FLIGHT) return resolve(entity, power, ability);
            if (ability.ability() == Ability.HOVER && hover == null) hover = power;
        }
        return hover == null ? null : resolve(entity, hover, (AbilityGeneType.Instance) hover.component());
    }

    private static FlightProfile resolve(LivingEntity entity, Power power, AbilityGeneType.Instance ability) {
        FlightStyle style = ability.style();
        boolean explicit = style != null;
        if (style == null) {
            style = ability.ability() == Ability.HOVER ? FlightStyle.DRIFT
                    : hasLiftAbility(entity) ? FlightStyle.FLAP : FlightStyle.GLIDE;
        }
        double cruise = Double.isNaN(ability.cruiseHeight()) ? style.cruiseHeight() : ability.cruiseHeight();
        return new FlightProfile(power, ability.ability(), style, cruise, explicit);
    }

    /** Whether the bearer has an active ability or AI trigger the AI may pump mid-flight for lift. */
    public static boolean hasLiftAbility(LivingEntity entity) {
        for (Power gene : Powers.active(entity)) {
            if (gene.component() instanceof ActiveAbilityGeneType.Instance active
                    && active.aiTrigger() == ActiveAbilityGeneType.AiTrigger.WHEN_FLYING) {
                return true;
            }
            if (gene.component() instanceof TriggerGeneType.Instance trigger
                    && trigger.trigger() == TriggerGeneType.Trigger.AI
                    && trigger.aiTrigger() == ActiveAbilityGeneType.AiTrigger.WHEN_FLYING) {
                return true;
            }
        }
        return false;
    }

    /** One line for debug reports. */
    public String describe() {
        return "style " + style.key() + (explicit ? "" : " (inferred)")
                + String.format(", cruise %.1f", cruiseHeight);
    }
}
