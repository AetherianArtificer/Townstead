package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.expression.ExpressionService;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/** {@code pheno:expression}: show a named data-driven cue over the current action subject. */
public final class ExpressionActionType implements ActionType {
    public static final String KEY = "pheno:expression";
    @Override public String key() { return KEY; }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation cue = DataPackLang.parseId(GsonHelper.getAsString(json, "cue", ""));
        if (cue == null) return null;
        return ctx -> {
            if (!ExpressionService.emit(ctx.entity(), cue, ctx.other())) ctx.fail();
        };
    }
}
