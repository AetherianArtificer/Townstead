package com.aetherianartificer.townstead.origin.collection;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.item.ItemAction;
import com.aetherianartificer.townstead.pheno.action.item.ItemActionType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/**
 * Adds/removes/clears the contextual stack's item in an {@code of: item} collection on the holder
 * (the item-domain {@code change_collection}; "remember the items I've used/collected"). Same id as
 * the entity and block variants, resolved by the action domain it sits in.
 */
public final class ChangeCollectionItemActionType implements ItemActionType {

    public static final String KEY = "pheno:change_collection";

    private enum Op { ADD, REMOVE, CLEAR }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public ItemAction parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "collection", ""));
        if (id == null) return null;
        Op op = switch (GsonHelper.getAsString(json, "operation", "add").toLowerCase(Locale.ROOT)) {
            case "remove" -> Op.REMOVE;
            case "clear" -> Op.CLEAR;
            default -> Op.ADD;
        };
        int rawTtl = json.has("time_limit") ? GsonHelper.getAsInt(json, "time_limit", 0) : 0;
        Integer ttl = rawTtl > 0 ? rawTtl : null;
        return ctx -> {
            if (ctx.holder() == null) return;
            switch (op) {
                case ADD -> CollectionValues.addItem(ctx.holder(), id, ctx.stack(), ttl);
                case REMOVE -> CollectionValues.removeElement(ctx.holder(), id, CollectionElement.ofItem(ctx.stack()));
                case CLEAR -> CollectionValues.clearOne(ctx.holder(), id);
            }
        };
    }
}
