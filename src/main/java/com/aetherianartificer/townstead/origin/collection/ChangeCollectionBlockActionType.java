package com.aetherianartificer.townstead.origin.collection;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/**
 * Adds/removes/clears the contextual block position in an {@code of: block} collection on the cause
 * entity (the block-domain {@code change_collection}, fed by a block action's {@code on} or a block
 * trigger). Same id as the entity and item variants, resolved by the action domain it sits in.
 */
public final class ChangeCollectionBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:change_collection";

    private enum Op { ADD, REMOVE, CLEAR }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
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
            if (ctx.cause() == null) return;
            switch (op) {
                case ADD -> CollectionValues.addBlock(ctx.cause(), id, ctx.pos(), ttl);
                case REMOVE -> CollectionValues.removeElement(ctx.cause(), id, CollectionElement.ofBlock(ctx.pos()));
                case CLEAR -> CollectionValues.clearOne(ctx.cause(), id);
            }
        };
    }
}
