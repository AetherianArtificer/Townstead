package com.aetherianartificer.townstead.pheno.action.item.types;

import com.aetherianartificer.townstead.pheno.action.item.ItemAction;
import com.aetherianartificer.townstead.pheno.action.item.ItemActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Shrinks the stack by {@code amount} (Apoli's item {@code consume}).
 *
 * <p>JSON: {@code { "type":"townstead_origins:consume", "amount":1 }}</p>
 */
public final class ConsumeItemActionType implements ItemActionType {

    public static final String KEY = "townstead_origins:consume";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public ItemAction parse(JsonObject json) {
        int amount = Math.max(1, GsonHelper.getAsInt(json, "amount", 1));
        return ctx -> ctx.stack().shrink(amount);
    }
}
