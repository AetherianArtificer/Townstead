package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.seek.Seeks;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Calls off the focus's seek by {@code id}, or all of them without one.
 *
 * <p>JSON: {@code { "type":"pheno:stop_seek", "id":"my_pack:rounds" }}</p>
 */
public final class StopSeekActionType implements ActionType {

    public static final String KEY = "pheno:stop_seek";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation id = json.has("id") ? DataPackLang.parseId(GsonHelper.getAsString(json, "id", "")) : null;
        if (json.has("id") && id == null) return null;
        return ctx -> {
            if (ctx.entity() != null && !ctx.level().isClientSide()) Seeks.stop(ctx.entity(), id);
        };
    }
}
