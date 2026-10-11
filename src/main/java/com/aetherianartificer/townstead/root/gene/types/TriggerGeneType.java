package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
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
 *
 * <p>{@code when_work} fires when the bearer completes a piece of Career work. It accepts
 * {@code verbs} (one or a list of work verbs such as {@code townstead:harvested}) and an
 * {@code item_condition} tested against the work's output. Block actions anchor at the worked
 * block when the work has one.</p>
 *
 * <p>{@code when_craft} fires when the bearer takes a crafted item; {@code item_condition} tests the
 * result and {@code item_action} changes it before it reaches the inventory.
 * {@code when_death_prevented} fires after a {@code prevent} death gene saves the bearer, with the
 * killer as the counterpart. Damage triggers expose their damage to {@code pheno:event_damage}.</p>
 *
 * <p>{@code bientity_condition} tests (bearer, counterpart) and fails when there is no counterpart,
 * so a lifesteal can skip foes that do not bleed.</p>
 *
 * <p>{@code ai} fires for villagers only, from their ability AI, while its {@code ai_trigger} holds
 * ({@code when_flying}, {@code when_threatened}, {@code when_hurt}, {@code when_hurt_nearby},
 * {@code always}) and its {@code condition} passes. Paired with {@code pheno:keypress} it lets a
 * villager use a power players drive with a key: {@code { "type":"pheno:trigger", "trigger":"ai",
 * "ai_trigger":"when_flying", "action":{ "type":"pheno:keypress", "key":"jump" } }} flaps the wings
 * a jump-press trigger beats.</p>
 */
public final class TriggerGeneType implements GeneType {

    public static final String KEY = "pheno:trigger";

    public enum Trigger { WHEN_HURT, WHEN_ATTACK, WHEN_KILL, WHEN_DEATH, WHEN_LAND, WHEN_WAKE_UP,
        WHEN_JUMP, WHEN_STRUCK_BY_LIGHTNING, WHEN_EQUIP, WHEN_ITEM_USE, WHEN_ENTER_DIMENSION, PRESS,
        WHEN_WORK, WHEN_CRAFT, WHEN_DEATH_PREVENTED, AI }

    public enum Target { SELF, OTHER }

    public record Instance(Trigger trigger, Target target, Action action, @Nullable Condition condition,
                           @Nullable com.aetherianartificer.townstead.pheno.condition.damage.DamageCondition damageCondition,
                           @Nullable String key,
                           @Nullable com.aetherianartificer.townstead.pheno.condition.item.ItemCondition itemCondition,
                           java.util.List<String> verbs,
                           @Nullable com.aetherianartificer.townstead.pheno.action.item.ItemAction itemAction,
                           @Nullable com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition bientityCondition,
                           /** When villagers' AI fires an {@code ai} trigger; {@code NEVER} for every other kind. */
                           ActiveAbilityGeneType.AiTrigger aiTrigger)
            implements GeneInstance {
        public Instance(Trigger trigger, Target target, Action action, @Nullable Condition condition,
                        @Nullable com.aetherianartificer.townstead.pheno.condition.damage.DamageCondition damageCondition,
                        @Nullable String key,
                        @Nullable com.aetherianartificer.townstead.pheno.condition.item.ItemCondition itemCondition,
                        java.util.List<String> verbs,
                        @Nullable com.aetherianartificer.townstead.pheno.action.item.ItemAction itemAction,
                        @Nullable com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition bientityCondition) {
            this(trigger, target, action, condition, damageCondition, key, itemCondition, verbs, itemAction,
                    bientityCondition, ActiveAbilityGeneType.AiTrigger.NEVER);
        }

        public Instance(Trigger trigger, Target target, Action action, @Nullable Condition condition,
                        @Nullable com.aetherianartificer.townstead.pheno.condition.damage.DamageCondition damageCondition,
                        @Nullable String key,
                        @Nullable com.aetherianartificer.townstead.pheno.condition.item.ItemCondition itemCondition,
                        java.util.List<String> verbs,
                        @Nullable com.aetherianartificer.townstead.pheno.action.item.ItemAction itemAction) {
            this(trigger, target, action, condition, damageCondition, key, itemCondition, verbs, itemAction, null);
        }

        public Instance(Trigger trigger, Target target, Action action, @Nullable Condition condition,
                        @Nullable com.aetherianartificer.townstead.pheno.condition.damage.DamageCondition damageCondition,
                        @Nullable String key,
                        @Nullable com.aetherianartificer.townstead.pheno.condition.item.ItemCondition itemCondition) {
            this(trigger, target, action, condition, damageCondition, key, itemCondition, java.util.List.of(), null);
        }

        /** {@code when_work} only: empty accepts every work verb. */
        public boolean acceptsVerb(String verb) {
            return verbs.isEmpty() || verbs.contains(verb);
        }

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
        // A craft trigger may only change the item; the entity action is then optional.
        Action action = json.has("action") || trigger != Trigger.WHEN_CRAFT
                ? Actions.parse(json.get("action")) : ctx -> {};
        if (action == null) return null;
        var itemAction = json.has("item_action")
                ? com.aetherianartificer.townstead.pheno.action.item.ItemActions.parse(json.get("item_action"))
                : null;
        if (json.has("item_action") && (itemAction == null || trigger != Trigger.WHEN_CRAFT)) return null;
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
        var itemCondition = json.has("item_condition")
                ? com.aetherianartificer.townstead.pheno.condition.item.ItemConditions.parse(json.get("item_condition"))
                : null;
        if (json.has("item_condition") && (itemCondition == null
                || (trigger != Trigger.WHEN_ITEM_USE && trigger != Trigger.WHEN_WORK
                        && trigger != Trigger.WHEN_CRAFT))) return null;
        java.util.List<String> verbs = new java.util.ArrayList<>();
        if (trigger == Trigger.WHEN_WORK && json.has("verbs")) {
            var raw = json.get("verbs");
            if (raw.isJsonArray()) {
                for (var element : raw.getAsJsonArray()) verbs.add(element.getAsString());
            } else {
                verbs.add(raw.getAsString());
            }
        }
        var bientityCondition = json.has("bientity_condition")
                ? com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditions.parse(
                        json.get("bientity_condition"))
                : null;
        if (json.has("bientity_condition") && bientityCondition == null) return null;
        ActiveAbilityGeneType.AiTrigger aiTrigger = trigger == Trigger.AI
                ? ActiveAbilityGeneType.AiTrigger.byKey(GsonHelper.getAsString(json, "ai_trigger", "never"))
                : ActiveAbilityGeneType.AiTrigger.NEVER;
        if (trigger == Trigger.AI && aiTrigger == ActiveAbilityGeneType.AiTrigger.NEVER) return null;
        return new Instance(trigger, target, action, condition, damageCondition, key, itemCondition,
                java.util.List.copyOf(verbs), itemAction, bientityCondition, aiTrigger);
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
            case "when_enter_dimension", "on_enter_dimension" -> Trigger.WHEN_ENTER_DIMENSION;
            case "press", "key", "key_press" -> Trigger.PRESS;
            case "ai", "villager_ai" -> Trigger.AI;
            case "when_work", "on_work", "work" -> Trigger.WHEN_WORK;
            case "when_craft", "on_craft", "craft" -> Trigger.WHEN_CRAFT;
            case "when_death_prevented", "on_death_prevented", "death_prevented", "when_saved" ->
                    Trigger.WHEN_DEATH_PREVENTED;
            default -> null;
        };
    }
}
