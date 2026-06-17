package com.aetherianartificer.townstead.origin.collection;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditions;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Runs {@code do} once per member of a collection (Apoli's {@code action_on_set}), with the holder
 * as {@code entity()} and the member as {@code other()}, optionally gated by a bi-entity
 * {@code where} and capped by {@code limit}. {@code order} is {@code oldest_first} (default) or
 * {@code newest_first} (Apoli's {@code reverse}). General iteration kept separate from the
 * collection's own operations: it does not act on the collection, it consumes one as input, so
 * later it can accept other selectors too.
 *
 * <p>JSON: {@code { "type":"pheno:for_each", "in":"my_pack:marked",
 * "do":{ "type":"pheno:target_action", "action":{...} }, "limit":4 }}</p>
 */
public final class ForEachActionType implements ActionType {

    public static final String KEY = "pheno:for_each";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "in", ""));
        if (id == null) return null;
        Action inner = Actions.parse(json.has("do") ? json.get("do") : json.get("action"));
        if (inner == null) return null;
        BiEntityCondition where = json.has("where") ? BiEntityConditions.parse(json.get("where")) : null;
        int limit = GsonHelper.getAsInt(json, "limit", 0);
        boolean newestFirst = "newest_first".equalsIgnoreCase(GsonHelper.getAsString(json, "order", "oldest_first"));
        return ctx -> {
            LivingEntity holder = ctx.entity();
            List<String> elements = CollectionValues.elements(holder, id);
            if (newestFirst) Collections.reverse(elements);
            int done = 0;
            for (String element : elements) {
                LivingEntity member = CollectionValues.resolveMember(holder, element);
                if (member == null) continue;
                if (where != null && !where.test(holder, member)) continue;
                inner.run(new ActionContext(holder, member));
                if (limit > 0 && ++done >= limit) break;
            }
        };
    }
}
