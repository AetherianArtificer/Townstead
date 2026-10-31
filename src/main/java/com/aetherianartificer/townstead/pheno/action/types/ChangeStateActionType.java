package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.state.EntityStates;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/** Shared implementation for {@code add_state} and {@code set_state}. */
public final class ChangeStateActionType implements ActionType {
    public enum Mode { ADD, SET }
    private final String key;
    private final Mode mode;

    public ChangeStateActionType(String key, Mode mode) {
        this.key = key;
        this.mode = mode;
    }

    @Override public String key() { return key; }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation state = DataPackLang.parseId(GsonHelper.getAsString(json, "state", ""));
        if (state == null) return null;
        Value amount = Values.parse(json.has("amount") ? json.get("amount") : null);
        if (amount == null && mode == Mode.ADD) amount = Values.constant(1);
        if (amount == null) return null;
        Value duration = json.has("duration") ? Values.parse(json.get("duration")) : Values.constant(0);
        if (duration == null) return null;
        ResourceLocation source = json.has("source")
                ? DataPackLang.parseId(GsonHelper.getAsString(json, "source", "")) : null;
        if (json.has("source") && source == null) return null;
        Value finalAmount = amount;
        return ctx -> {
            SelectorContext values = SelectorContext.of(ctx);
            double resolvedAmount = finalAmount.get(values);
            long resolvedDuration = Math.max(0, Math.round(duration.get(values)));
            boolean success = mode == Mode.ADD
                    ? EntityStates.add(ctx.entity(), state, resolvedAmount, resolvedDuration, source)
                    : EntityStates.set(ctx.entity(), state, resolvedAmount, resolvedDuration, source);
            if (!success) ctx.fail();
        };
    }
}
