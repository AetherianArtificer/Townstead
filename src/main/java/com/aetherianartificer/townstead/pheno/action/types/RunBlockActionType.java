package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.aetherianartificer.townstead.pheno.action.block.BlockActions;
import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;

/**
 * Bridges an entity context to a block action: runs the wrapped {@code block_action} at
 * the actor's block position, with the actor as the cause. This is how an entity-driven
 * power (a trigger, aura or active ability) reaches blocks. Use the block {@code offset}
 * / {@code area_of_effect} metas to shift or spread the effect from the actor.
 *
 * <p>JSON: {@code { "type":"pheno:block_action",
 * "block_action":{ "type":"pheno:explode", "power":3 } }}</p>
 */
public final class RunBlockActionType implements ActionType {

    public static final String KEY = "pheno:block_action";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        BlockAction block = BlockActions.parse(json.get("block_action"));
        if (block == null) return null;
        return ctx -> {
            if (ctx.entity().level() instanceof ServerLevel level) {
                block.run(new BlockActionContext(level, ctx.entity().blockPosition(), ctx.entity()));
            }
        };
    }
}
