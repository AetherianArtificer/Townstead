package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.state.EntityStates;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/** Clears eligible writable backing contributions without touching observed foreign effects. */
public final class ClearStateActionType implements ActionType {
    public static final String KEY = "pheno:clear_state";

    @Override public String key() { return KEY; }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation state = DataPackLang.parseId(GsonHelper.getAsString(json, "state", ""));
        if (state == null) return null;
        ResourceLocation source = json.has("source")
                ? DataPackLang.parseId(GsonHelper.getAsString(json, "source", "")) : null;
        if (json.has("source") && source == null) return null;
        return ctx -> {
            if (!EntityStates.clear(ctx.entity(), state, source)) ctx.fail();
        };
    }
}
