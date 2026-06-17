package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Always returns a fixed {@code value} (Apoli's {@code constant}). Useful as a
 * placeholder while authoring or to disable a gate without deleting it.
 *
 * <p>JSON: {@code { "type":"pheno:constant", "value":true }}</p>
 */
public final class ConstantConditionType implements ConditionType {

    public static final String KEY = "pheno:constant";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        boolean value = GsonHelper.getAsBoolean(json, "value", true);
        return value ? Conditions.ALWAYS : ctx -> false;
    }
}
