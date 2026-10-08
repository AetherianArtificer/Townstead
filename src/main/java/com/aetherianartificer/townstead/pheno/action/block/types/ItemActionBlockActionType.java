package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.aetherianartificer.townstead.pheno.action.item.ItemAction;
import com.aetherianartificer.townstead.pheno.action.item.ItemActionContext;
import com.aetherianartificer.townstead.pheno.action.item.ItemActions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/** Runs an ordinary item action on a block transaction's named item role. */
public final class ItemActionBlockActionType implements BlockActionType {
    public static final String KEY = "pheno:item_action";

    @Override public String key() { return KEY; }

    @Override public BlockAction parse(JsonObject json) {
        String role = GsonHelper.getAsString(json, "item", "item");
        ItemAction action = ItemActions.parse(json.get("action"));
        if (role.isBlank() || action == null) return null;
        return new BlockAction() {
            @Override public boolean canRun(BlockActionContext context) {
                // Missing roles are expected during the Job engine's block-only readiness probe.
                return true;
            }

            @Override public void run(BlockActionContext context) {
                var stack = context.itemRole(role);
                if (stack.isEmpty()) { context.fail(); return; }
                action.run(new ItemActionContext(stack, context.cause()));
                context.setItemRole(role, stack);
            }
        };
    }
}
