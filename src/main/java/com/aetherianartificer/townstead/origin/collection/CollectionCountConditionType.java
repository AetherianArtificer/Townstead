package com.aetherianartificer.townstead.origin.collection;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Comparison;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Compares the target's tally in the actor's {@code collection} store against {@code compare_to}
 * using {@code comparison} (Apugli's bi-entity {@code hits_on_target}). The actor holds the
 * collection; the target is the counted member. Sibling to {@code collection_size}, which counts
 * members rather than reading one member's tally; and to {@code collection_contains}, the
 * presence-only form.
 *
 * <p>JSON: {@code { "type":"pheno:collection_count", "collection":"my_pack:combo",
 * "comparison":">=", "compare_to":3 }}</p>
 */
public final class CollectionCountConditionType implements BiEntityConditionType {

    public static final String KEY = "pheno:collection_count";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BiEntityCondition parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "collection", ""));
        if (id == null) return null;
        Comparison comparison = Comparison.parse(GsonHelper.getAsString(json, "comparison", ">="));
        int compareTo = GsonHelper.getAsInt(json, "compare_to", 1);
        return (actor, target) -> comparison.compare(CollectionValues.countEntity(actor, id, target), compareTo);
    }
}
