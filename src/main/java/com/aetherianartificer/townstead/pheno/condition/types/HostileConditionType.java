package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.root.disposition.Hostility;
import com.google.gson.JsonObject;

/**
 * {@code pheno:hostile}: the entity is a threat to the other party (a monster, a mob targeting
 * it, or hostile by disposition; see {@link Hostility}). Inside a selector's {@code where} the
 * candidate is the entity and the one selecting is the other, so this reads "hostile to me".
 * False when there is no other.
 *
 * <p>JSON: {@code { "type":"pheno:hostile" }}</p>
 */
public final class HostileConditionType implements ConditionType {
    public static final String KEY = "pheno:hostile";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        return ctx -> ctx.entity() != null && ctx.other() != null && Hostility.toward(ctx.other(), ctx.entity());
    }
}
