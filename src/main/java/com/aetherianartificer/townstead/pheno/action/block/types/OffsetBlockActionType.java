package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.aetherianartificer.townstead.pheno.action.block.BlockActions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Shifts the target block by {@code (x,y,z)} before running the wrapped block action
 * (Apoli's block {@code offset}).
 *
 * <p>JSON: {@code { "type":"pheno:offset", "y":1,
 * "block_action":{ "type":"pheno:set_block", "block":"minecraft:torch" } }}</p>
 */
public final class OffsetBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:offset";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        int x = GsonHelper.getAsInt(json, "x", 0);
        int y = GsonHelper.getAsInt(json, "y", 0);
        int z = GsonHelper.getAsInt(json, "z", 0);
        BlockAction inner = BlockActions.parse(json.get("block_action"));
        if (inner == null) return null;
        return ctx -> inner.run(ctx.at(ctx.pos().offset(x, y, z)));
    }
}
