package com.aetherianartificer.townstead.pheno.action.item.types;

import com.aetherianartificer.townstead.pheno.action.item.ItemAction;
import com.aetherianartificer.townstead.pheno.action.item.ItemActionType;
import com.google.gson.JsonObject;

/**
 * Removes the extra anvil cost the stack gathered from earlier anvil work.
 *
 * <p>JSON: {@code { "type":"pheno:reset_repair_cost" }}</p>
 */
public final class ResetRepairCostItemActionType implements ItemActionType {

    public static final String KEY = "pheno:reset_repair_cost";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public ItemAction parse(JsonObject json) {
        return ctx -> {
            if (ctx.stack().isEmpty()) return;
            //? if >=1.21 {
            ctx.stack().set(net.minecraft.core.component.DataComponents.REPAIR_COST, 0);
            //?} else {
            /*ctx.stack().setRepairCost(0);
            *///?}
        };
    }
}
