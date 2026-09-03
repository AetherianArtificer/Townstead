package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.aetherianartificer.townstead.pheno.action.block.BlockActions;
import com.google.gson.JsonObject;

/** Runs one block action a strictly bounded number of times, preserving its item-role custody. */
public final class RepeatBlockActionType implements BlockActionType {
    public static final String KEY = "pheno:repeat";
    public static final int MAX_TIMES = 64;

    @Override public String key() { return KEY; }

    @Override
    public BlockAction parse(JsonObject json) {
        if (json == null || !json.has("times") || !json.get("times").isJsonPrimitive()
                || !json.getAsJsonPrimitive("times").isNumber()) return null;
        double raw = json.get("times").getAsDouble();
        int times = (int) raw;
        if (raw != times || times < 1 || times > MAX_TIMES) return null;
        BlockAction child = BlockActions.parse(json.get("block_action"));
        if (child == null) return null;
        return context -> {
            for (int i = 0; i < times && context.succeeded(); i++) child.run(context);
        };
    }
}
