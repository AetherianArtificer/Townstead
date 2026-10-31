package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.event.AttackCharge;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * True when the entity's attack charge is within {@code [min,max]} (0-1). Inside a hurt or attack
 * trigger this is the charge of the swing that landed; elsewhere it is the live bar. Mobs and
 * villagers always count as fully charged.
 *
 * <p>JSON: {@code { "type":"pheno:attack_strength", "min":0.9 }}</p>
 */
public final class AttackStrengthConditionType implements ConditionType {

    public static final String KEY = "pheno:attack_strength";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        float min = GsonHelper.getAsFloat(json, "min", 0f);
        float max = GsonHelper.getAsFloat(json, "max", 1f);
        return ctx -> {
            if (ctx.entity() == null) return false;
            float charge = AttackCharge.of(ctx.entity());
            return charge >= min && charge <= max;
        };
    }
}
