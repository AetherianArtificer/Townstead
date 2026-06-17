package com.aetherianartificer.townstead.origin.collection;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/**
 * Mutates a {@code collection} store on the actor (Apoli's {@code add_to_set} /
 * {@code remove_from_set}, plus {@code clear}; Apugli's {@code change_hits_on_target}), folded into
 * one type by {@code operation} the way {@code change_resource} folds add/set. The element is the
 * contextual {@code other()}, aimed with the usual {@code target_action} / {@code on} wrappers, so
 * {@code add_to_set} is just {@code change_collection} with {@code operation: add}. {@code time_limit}
 * (ticks) gives the entry a TTL (else the collection's {@code forget_after}).
 *
 * <p>Without {@code amount}, {@code add} ensures the member is present (tally one) and {@code remove}
 * drops it. With {@code amount}, {@code add} adjusts the member's tally and {@code set} assigns it;
 * a tally falling to zero drops the member. So a plain set and a counter are one action.</p>
 *
 * <p>JSON: {@code { "type":"pheno:change_collection", "collection":"my_pack:marked",
 * "operation":"add" }} or {@code { ..., "operation":"add", "amount":1 }}</p>
 */
public final class ChangeCollectionActionType implements ActionType {

    public static final String KEY = "pheno:change_collection";

    private enum Op { ADD, SET, REMOVE, CLEAR }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "collection", ""));
        if (id == null) return null;
        Op op = switch (GsonHelper.getAsString(json, "operation", "add").toLowerCase(Locale.ROOT)) {
            case "set" -> Op.SET;
            case "remove" -> Op.REMOVE;
            case "clear" -> Op.CLEAR;
            default -> Op.ADD;
        };
        int rawTtl = json.has("time_limit") ? GsonHelper.getAsInt(json, "time_limit", 0) : 0;
        Integer ttl = rawTtl > 0 ? rawTtl : null;
        // A counted change opts in with "amount"; bare add/remove keep set semantics (tally one).
        boolean counted = json.has("amount") || op == Op.SET;
        int amount = GsonHelper.getAsInt(json, "amount", 1);
        // of:entity uses the contextual other(); of:key adds a literal key string instead.
        String key = json.has("key") ? GsonHelper.getAsString(json, "key", "") : null;
        return ctx -> {
            switch (op) {
                case ADD -> {
                    if (counted) {
                        if (key != null) CollectionValues.changeCount(ctx.entity(), id, key, amount, false, ttl);
                        else if (ctx.other() != null) CollectionValues.changeCountEntity(ctx.entity(), id, ctx.other(), amount, false, ttl);
                    } else if (key != null) {
                        CollectionValues.addElement(ctx.entity(), id, key, ttl);
                    } else if (ctx.other() != null) {
                        CollectionValues.add(ctx.entity(), id, ctx.other(), ttl);
                    }
                }
                case SET -> {
                    if (key != null) CollectionValues.changeCount(ctx.entity(), id, key, amount, true, ttl);
                    else if (ctx.other() != null) CollectionValues.changeCountEntity(ctx.entity(), id, ctx.other(), amount, true, ttl);
                }
                case REMOVE -> {
                    if (key != null) CollectionValues.removeElement(ctx.entity(), id, key);
                    else if (ctx.other() != null) CollectionValues.remove(ctx.entity(), id, ctx.other());
                }
                case CLEAR -> CollectionValues.clearOne(ctx.entity(), id);
            }
        };
    }
}
