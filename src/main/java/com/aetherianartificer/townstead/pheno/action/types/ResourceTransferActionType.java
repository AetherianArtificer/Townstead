package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.origin.ability.ResourceValues;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Moves up to {@code amount} between two of the actor's {@code resource} meters
 * (Apugli's {@code resource_transfer}). Only the amount actually drained from
 * {@code from} (after its min clamp) is added to {@code to}, so the transfer conserves.
 *
 * <p>JSON: {@code { "type":"pheno:resource_transfer", "from":"my_pack:rage",
 * "to":"my_pack:focus", "amount":5 }}</p>
 */
public final class ResourceTransferActionType implements ActionType {

    public static final String KEY = "pheno:resource_transfer";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation from = DataPackLang.parseId(GsonHelper.getAsString(json, "from", ""));
        ResourceLocation to = DataPackLang.parseId(GsonHelper.getAsString(json, "to", ""));
        int amount = GsonHelper.getAsInt(json, "amount", 0);
        if (from == null || to == null || amount <= 0) return null;
        return ctx -> {
            int before = ResourceValues.get(ctx.entity(), from);
            ResourceValues.change(ctx.entity(), from, -amount);
            int drained = before - ResourceValues.get(ctx.entity(), from);
            if (drained > 0) ResourceValues.change(ctx.entity(), to, drained);
        };
    }
}
