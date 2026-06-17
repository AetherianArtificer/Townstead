package com.aetherianartificer.townstead.origin.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Comparison;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.origin.ability.ResourceValues;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Compares a {@code resource} gene's value against either a fixed {@code compare_to}
 * number or another resource gene ({@code compared_to_resource}), using
 * {@code comparison} (Apugli's {@code compare_resource}). Genetics-specific, so it
 * lives in {@code origin} rather than the shared layer.
 *
 * <p>JSON: {@code { "type":"pheno:compare_resource", "resource":"my_pack:mana",
 * "comparison":">=", "compare_to":10 }}</p>
 */
public final class CompareResourceConditionType implements ConditionType {

    public static final String KEY = "pheno:compare_resource";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation resource = DataPackLang.parseId(GsonHelper.getAsString(json, "resource", ""));
        if (resource == null) return null;
        Comparison comparison = Comparison.parse(GsonHelper.getAsString(json, "comparison", ">="));
        @Nullable ResourceLocation other = json.has("compared_to_resource")
                ? DataPackLang.parseId(GsonHelper.getAsString(json, "compared_to_resource", ""))
                : null;
        int compareTo = GsonHelper.getAsInt(json, "compare_to", 0);
        return ctx -> {
            int value = ResourceValues.get(ctx.entity(), resource);
            int rhs = other != null ? ResourceValues.get(ctx.entity(), other) : compareTo;
            return comparison.compare(value, rhs);
        };
    }
}
