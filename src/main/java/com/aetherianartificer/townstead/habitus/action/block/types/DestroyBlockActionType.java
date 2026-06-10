package com.aetherianartificer.townstead.habitus.action.block.types;

import com.aetherianartificer.townstead.habitus.action.block.BlockAction;
import com.aetherianartificer.townstead.habitus.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Destroys the target block (Apugli's {@code destroy}). {@code drop_item} controls
 * whether it drops as in survival breaking; the {@code cause} entity (if any) is
 * credited as the breaker.
 *
 * <p>JSON: {@code { "type":"townstead_origins:destroy", "drop_item":true }}</p>
 */
public final class DestroyBlockActionType implements BlockActionType {

    public static final String KEY = "townstead_origins:destroy";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        boolean drop = GsonHelper.getAsBoolean(json, "drop_item", true);
        return ctx -> ctx.level().destroyBlock(ctx.pos(), drop, ctx.cause());
    }
}
