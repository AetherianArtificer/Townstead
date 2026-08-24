package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.aetherianartificer.townstead.pheno.action.block.BlockActions;
import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.google.gson.JsonObject;

/** Selects one of two block actions using an ordinary block condition. */
public final class IfElseBlockActionType implements BlockActionType {
    public static final String KEY = "pheno:if_else";

    @Override public String key() { return KEY; }

    @Override public BlockAction parse(JsonObject json) {
        BlockCondition condition = BlockConditions.parse(json.get("if"));
        BlockAction thenAction = BlockActions.parse(json.get("then"));
        BlockAction elseAction = json.has("else") ? BlockActions.parse(json.get("else")) : null;
        if (condition == null || thenAction == null || (json.has("else") && elseAction == null)) return null;
        return new BlockAction() {
            private BlockAction selected(BlockActionContext context) {
                return condition.test(context.level(), context.pos()) ? thenAction : elseAction;
            }

            @Override public boolean canRun(BlockActionContext context) {
                BlockAction selected = selected(context);
                return selected == null || selected.canRun(context);
            }

            @Override public void run(BlockActionContext context) {
                BlockAction selected = selected(context);
                if (selected != null) selected.run(context);
            }
        };
    }
}
