package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.aetherianartificer.townstead.pheno.action.block.BlockActions;
import com.aetherianartificer.townstead.pheno.selector.BlockSelector;
import com.aetherianartificer.townstead.pheno.selector.BlockSelectors;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Runs a block action at blocks chosen from an entity context: the bridge from an entity action to
 * a block action (a raycast's block hit, a place, a region). The {@code blocks} field is a block
 * selector (not {@code on}, which an entity action reserves for entity selection) and {@code do} is
 * the block action run at each. The acting entity is the block action's cause.
 *
 * <p>JSON: {@code { "type":"pheno:at", "blocks":{ "type":"pheno:ray", "stop_on":"block" },
 * "do":{ "type":"pheno:set_block", "block":"minecraft:fire" } }}</p>
 */
public final class AtActionType implements ActionType {

    public static final String KEY = "pheno:at";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        BlockSelector selector = json.has("blocks") ? BlockSelectors.parse(json.get("blocks")) : null;
        BlockAction action = json.has("do") ? BlockActions.parse(json.get("do")) : null;
        if (selector == null || action == null) return null;
        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel level)) return;
            SelectorContext sc = SelectorContext.of(ctx);
            for (BlockPos pos : selector.select(sc)) {
                action.run(new BlockActionContext(level, pos, ctx.entity()));
            }
        };
    }
}
