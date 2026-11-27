package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.combat.Guards;
import com.aetherianartificer.townstead.pheno.condition.damage.DamageCondition;
import com.aetherianartificer.townstead.pheno.condition.damage.DamageConditions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Links the focus to the bearer for {@code duration} ticks: {@code fraction} of each hit on the
 * focus goes to the bearer instead. {@code reach} (default 0, no limit) is the distance past which
 * the link does nothing. An optional {@code damage_condition} limits it to matching damage.
 * {@code on_share} runs on the bearer each time, with the guarded entity as the counterpart and
 * the moved amount readable as {@code pheno:event_damage}.
 *
 * <p>JSON: {@code { "type":"pheno:share_damage", "id":"guard", "fraction":0.5, "duration":300 }}</p>
 */
public final class ShareDamageActionType implements ActionType {

    public static final String KEY = "pheno:share_damage";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        float fraction = Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(json, "fraction", 0.5f)));
        int duration = Math.max(1, GsonHelper.getAsInt(json, "duration", 100));
        float reach = Math.max(0f, GsonHelper.getAsFloat(json, "reach", 0f));
        String id = GsonHelper.getAsString(json, "id", "guard");
        DamageCondition damageCondition = json.has("damage_condition")
                ? DamageConditions.parse(json.get("damage_condition")) : null;
        if (json.has("damage_condition") && damageCondition == null) return null;
        Action onShare = json.has("on_share") ? Actions.parse(json.get("on_share")) : null;
        Guards.Spec spec = new Guards.Spec(id, fraction, duration, reach, damageCondition, onShare);
        return ctx -> {
            if (ctx.entity() == null || ctx.origin() == null) return;
            Guards.place(ctx.entity(), ctx.origin(), spec);
        };
    }
}
