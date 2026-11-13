package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.combat.Wards;
import com.aetherianartificer.townstead.pheno.condition.damage.DamageCondition;
import com.aetherianartificer.townstead.pheno.condition.damage.DamageConditions;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Places a damage ward on the focus: it spares up to {@code amount} health in total, taking
 * {@code fraction} of each hit (default all of it), for {@code duration} ticks. An optional
 * {@code damage_condition} limits it to matching damage. {@code on_absorb} runs on the warded
 * entity each time it spares damage and {@code on_break} when its budget runs out, both with the
 * attacker as the counterpart and the spared amount readable as {@code pheno:event_damage}.
 * {@code on_end} runs when the time runs out before the budget does, with the total spared as the
 * event damage. In all three, {@code origin} is the entity that placed the ward. A ward with
 * {@code fraction} 0 spares nothing: {@code on_absorb} runs on every matching hit, with the whole hit
 * as the event damage, even while another ward spares that hit.
 *
 * <p>Wards never stack: a hit is softened only by the ward that spares the most. A ward with the
 * same {@code id} (default {@code "ward"}) replaces the previous one.</p>
 *
 * <p>JSON: {@code { "type":"pheno:ward", "id":"prism", "amount":4, "fraction":0.5,
 * "duration":120, "damage_condition":{ "type":"pheno:projectile" } }}</p>
 */
public final class WardActionType implements ActionType {

    public static final String KEY = "pheno:ward";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        Value amount = json.has("amount") ? Values.parse(json.get("amount")) : null;
        if (amount == null) return null;
        float fraction = Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(json, "fraction", 1f)));
        int duration = Math.max(1, GsonHelper.getAsInt(json, "duration", 100));
        String id = GsonHelper.getAsString(json, "id", "ward");
        DamageCondition damageCondition = json.has("damage_condition")
                ? DamageConditions.parse(json.get("damage_condition")) : null;
        if (json.has("damage_condition") && damageCondition == null) return null;
        Action onAbsorb = json.has("on_absorb") ? Actions.parse(json.get("on_absorb")) : null;
        Action onBreak = json.has("on_break") ? Actions.parse(json.get("on_break")) : null;
        Action onEnd = json.has("on_end") ? Actions.parse(json.get("on_end")) : null;
        Wards.Spec spec = new Wards.Spec(id, fraction, duration, damageCondition, onAbsorb, onBreak, onEnd);
        return ctx -> Wards.place(ctx.entity(), spec, (float) amount.get(SelectorContext.of(ctx)), ctx.origin());
    }
}
