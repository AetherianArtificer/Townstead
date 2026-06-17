package com.aetherianartificer.townstead.origin.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.origin.ability.ResourceValues;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * True when the entity's value for a {@code resource} gene is within {@code [min, max]}
 * (Apoli's {@code resource}). A genetics-specific condition, so it lives in {@code origin}
 * (which depends on pheno) rather than the shared layer.
 *
 * <p>JSON: {@code { "type":"pheno:resource", "resource":"my_pack:mana", "min":1 }}</p>
 */
public final class ResourceConditionType implements ConditionType {

    public static final String KEY = "pheno:resource";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "resource", ""));
        if (id == null) return null;
        int min = GsonHelper.getAsInt(json, "min", Integer.MIN_VALUE);
        int max = GsonHelper.getAsInt(json, "max", Integer.MAX_VALUE);
        return ctx -> {
            int value = ResourceValues.get(ctx.entity(), id);
            return value >= min && value <= max;
        };
    }
}
