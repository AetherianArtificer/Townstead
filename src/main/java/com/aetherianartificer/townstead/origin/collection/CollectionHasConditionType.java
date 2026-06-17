package com.aetherianartificer.townstead.origin.collection;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Tests whether the holder's collection contains a literal {@code key} (for {@code of: key} flag
 * sets) or a given {@code item} id (for {@code of: item} sets). The same {@code collection_contains}
 * id as the bi-entity entity-membership test, resolved here in the entity-condition slot.
 *
 * <p>JSON: {@code { "type":"pheno:collection_contains", "collection":"my_pack:visited", "key":"village_x" }}</p>
 */
public final class CollectionHasConditionType implements ConditionType {

    public static final String KEY = "pheno:collection_contains";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "collection", ""));
        if (id == null) return null;
        String element;
        if (json.has("key")) {
            element = GsonHelper.getAsString(json, "key", "");
        } else if (json.has("item")) {
            ResourceLocation item = DataPackLang.parseId(GsonHelper.getAsString(json, "item", ""));
            element = item == null ? null : item.toString();
        } else {
            element = null;
        }
        if (element == null) return null;
        String value = element;
        return ctx -> CollectionValues.containsElement(ctx.entity(), id, value);
    }
}
