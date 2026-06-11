package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.ability.ResourceValues;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Moves one of the actor's {@code resource} meters by {@code amount} (clamped to the
 * resource's range).
 *
 * <p>JSON: {@code { "type":"townstead_origins:change_resource",
 * "resource":"my_pack:blood", "amount":-10 }}</p>
 */
public final class ChangeResourceActionType implements ActionType {

    public static final String KEY = "townstead_origins:change_resource";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation resource = DataPackLang.parseId(GsonHelper.getAsString(json, "resource", ""));
        if (resource == null) return null;
        int amount = GsonHelper.getAsInt(json, "amount", 0);
        boolean set = "set".equalsIgnoreCase(GsonHelper.getAsString(json, "operation", "add"));
        return ctx -> {
            if (set) ResourceValues.set(ctx.entity(), resource, amount);
            else ResourceValues.change(ctx.entity(), resource, amount);
        };
    }
}
