package com.aetherianartificer.townstead.origin.collection;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Comparison;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Compares the size of the holder's {@code collection} store against {@code compare_to} using
 * {@code comparison} (Apoli's {@code set_size}), mirroring {@code compare_resource}.
 *
 * <p>JSON: {@code { "type":"pheno:collection_size", "collection":"my_pack:marked",
 * "comparison":">=", "compare_to":3 }}</p>
 */
public final class CollectionSizeConditionType implements ConditionType {

    public static final String KEY = "pheno:collection_size";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "collection", ""));
        if (id == null) return null;
        Comparison comparison = Comparison.parse(GsonHelper.getAsString(json, "comparison", ">="));
        int compareTo = GsonHelper.getAsInt(json, "compare_to", 0);
        return ctx -> comparison.compare(CollectionValues.size(ctx.entity(), id), compareTo);
    }
}
