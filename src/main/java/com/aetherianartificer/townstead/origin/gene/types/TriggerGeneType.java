package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.habitus.action.Action;
import com.aetherianartificer.townstead.habitus.action.Actions;
import com.aetherianartificer.townstead.habitus.condition.Condition;
import com.aetherianartificer.townstead.habitus.condition.Conditions;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Runs an {@link Action} when a combat event involving the bearer fires (Apoli's
 * {@code action_on_hit} / {@code action_when_hit} / {@code action_on_kill} family).
 * {@code target} picks which entity the action's primary actor is: {@code self} (the
 * bearer) or {@code other} (the counterpart, e.g. the attacker for {@code when_hurt}
 * or the victim for {@code when_attack}); the counterpart is available via
 * {@code ActionContext.other()}. Dispatched server-side by {@code GeneTriggers} off
 * the damage and death events.
 *
 * <p>JSON: {@code { "type":"townstead_origins:trigger", "trigger":"when_hurt",
 * "target":"other", "action":{ "type":"townstead_origins:ignite", "seconds":3 },
 * "condition":{ "type":"townstead_origins:on_fire" } }} (thorns-style retaliation).</p>
 */
public final class TriggerGeneType implements GeneType {

    public static final String KEY = "townstead_origins:trigger";

    public enum Trigger { WHEN_HURT, WHEN_ATTACK, WHEN_KILL, WHEN_DEATH, WHEN_LAND, WHEN_WAKE_UP,
        WHEN_JUMP, WHEN_STRUCK_BY_LIGHTNING, WHEN_EQUIP }

    public enum Target { SELF, OTHER }

    public record Instance(Trigger trigger, Target target, Action action, @Nullable Condition condition,
                           @Nullable com.aetherianartificer.townstead.habitus.condition.damage.DamageCondition damageCondition)
            implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        Trigger trigger = parseTrigger(GsonHelper.getAsString(json, "trigger", ""));
        if (trigger == null) return null;
        Action action = Actions.parse(json.get("action"));
        if (action == null) return null;
        Target target = "other".equalsIgnoreCase(GsonHelper.getAsString(json, "target", "self"))
                ? Target.OTHER : Target.SELF;
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        com.aetherianartificer.townstead.habitus.condition.damage.DamageCondition damageCondition =
                json.has("damage_condition")
                        ? com.aetherianartificer.townstead.habitus.condition.damage.DamageConditions.parse(
                                json.get("damage_condition"))
                        : null;
        return new Instance(trigger, target, action, condition, damageCondition);
    }

    @Nullable
    private static Trigger parseTrigger(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "when_hurt", "after_hurt", "hurt" -> Trigger.WHEN_HURT;
            case "when_attack", "on_hit", "hit" -> Trigger.WHEN_ATTACK;
            case "when_kill", "on_kill", "kill" -> Trigger.WHEN_KILL;
            case "when_death", "on_death", "death" -> Trigger.WHEN_DEATH;
            case "when_land", "on_land", "land" -> Trigger.WHEN_LAND;
            case "when_wake_up", "on_wake_up", "wake_up", "wakeup" -> Trigger.WHEN_WAKE_UP;
            case "when_jump", "on_jump", "jump" -> Trigger.WHEN_JUMP;
            case "when_struck_by_lightning", "when_lightning_struck", "on_lightning_struck", "lightning_struck" ->
                    Trigger.WHEN_STRUCK_BY_LIGHTNING;
            case "when_equip", "on_equip", "equip" -> Trigger.WHEN_EQUIP;
            default -> null;
        };
    }
}
