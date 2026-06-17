package com.aetherianartificer.townstead.origin.collection;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Tests whether the target is in the actor's {@code collection} store (Apoli's bi-entity
 * {@code in_set}). The actor holds the collection; the target is the member being tested.
 *
 * <p>JSON: {@code { "type":"pheno:collection_contains", "collection":"my_pack:marked" }}</p>
 */
public final class CollectionContainsConditionType implements BiEntityConditionType {

    public static final String KEY = "pheno:collection_contains";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BiEntityCondition parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "collection", ""));
        if (id == null) return null;
        return (actor, target) -> CollectionValues.contains(actor, id, target);
    }
}
