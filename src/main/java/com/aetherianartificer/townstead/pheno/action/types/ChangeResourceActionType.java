package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.ability.ResourceValues;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Moves one of the actor's {@code resource} meters by {@code amount} (clamped to the
 * resource's range).
 *
 * <p>JSON: {@code { "type":"pheno:change_resource",
 * "resource":"my_pack:blood", "amount":-10 }}</p>
 */
public final class ChangeResourceActionType implements ActionType {

    public static final String KEY = "pheno:change_resource";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation resource = DataPackLang.parseId(GsonHelper.getAsString(json, "resource", ""));
        if (resource == null) return null;
        Value amount = json.has("amount") ? Values.parse(json.get("amount")) : Values.constant(0);
        if (amount == null) return null;
        // "subtract" negates, matching how a resource cost reads; "set" assigns; default adds.
        String operation = GsonHelper.getAsString(json, "operation", "add").toLowerCase(java.util.Locale.ROOT);
        boolean set = "set".equals(operation);
        boolean subtract = "subtract".equals(operation);
        return ctx -> {
            int value = (int) Math.round(amount.get(SelectorContext.of(ctx)));
            if (set) ResourceValues.set(ctx.entity(), resource, value);
            else ResourceValues.change(ctx.entity(), resource, subtract ? -value : value);
        };
    }
}
