package com.aetherianartificer.townstead.origin.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.origin.ability.Abilities;
import com.aetherianartificer.townstead.origin.ability.Ability;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * True when the entity currently has a given innate {@code ability} active (Apoli's
 * {@code ability} / {@code power_active} for the ability subset): a passive ability that
 * is expressed and its condition met, or a toggle ability that is on.
 *
 * <p>JSON: {@code { "type":"pheno:ability", "ability":"climbing" }}</p>
 */
public final class AbilityConditionType implements ConditionType {

    public static final String KEY = "pheno:ability";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Ability ability = Ability.byKey(GsonHelper.getAsString(json, "ability", ""));
        if (ability == null) return null;
        return ctx -> Abilities.isActive(ctx.entity(), ability);
    }
}
