package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
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
 * <p>JSON: {@code { "type":"pheno:trigger", "trigger":"when_hurt",
 * "target":"other", "action":{ "type":"pheno:ignite", "seconds":3 },
 * "condition":{ "type":"pheno:on_fire" } }} (thorns-style retaliation).</p>
 */
public final class TriggerGeneType implements GeneType {

    public static final String KEY = "pheno:trigger";

    public enum Trigger { WHEN_HURT, WHEN_ATTACK, WHEN_KILL, WHEN_DEATH, WHEN_LAND, WHEN_WAKE_UP,
        WHEN_JUMP, WHEN_STRUCK_BY_LIGHTNING, WHEN_EQUIP, WHEN_ITEM_USE, PRESS }

    public enum Target { SELF, OTHER }

    public record Instance(Trigger trigger, Target target, Action action, @Nullable Condition condition,
                           @Nullable com.aetherianartificer.townstead.pheno.condition.damage.DamageCondition damageCondition,
                           @Nullable String key)
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
        com.aetherianartificer.townstead.pheno.condition.damage.DamageCondition damageCondition =
                json.has("damage_condition")
                        ? com.aetherianartificer.townstead.pheno.condition.damage.DamageConditions.parse(
                                json.get("damage_condition"))
                        : null;
        // The keybind name is only meaningful for the press trigger.
        String key = trigger == Trigger.PRESS ? GsonHelper.getAsString(json, "key", "jump") : null;
        return new Instance(trigger, target, action, condition, damageCondition, key);
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
            case "when_item_use", "on_item_use", "item_use", "action_on_item_use" -> Trigger.WHEN_ITEM_USE;
            case "press", "key", "key_press" -> Trigger.PRESS;
            default -> null;
        };
    }
}
