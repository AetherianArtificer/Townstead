package com.aetherianartificer.townstead.pheno.action.item.types;

import com.aetherianartificer.townstead.pheno.action.item.ItemAction;
import com.aetherianartificer.townstead.pheno.action.item.ItemActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;

/**
 * Applies durability damage to the stack (Apoli's item {@code damage}); the item breaks
 * (shrinks) when it would exceed its max. Negative amounts repair. Uses the
 * version-uniform {@code getDamageValue}/{@code setDamageValue}.
 *
 * <p>JSON: {@code { "type":"pheno:damage", "amount":1 }}</p>
 */
public final class DamageItemActionType implements ItemActionType {

    public static final String KEY = "pheno:damage";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public ItemAction parse(JsonObject json) {
        int amount = GsonHelper.getAsInt(json, "amount", 1);
        return ctx -> {
            ItemStack stack = ctx.stack();
            if (amount == 0 || !stack.isDamageableItem()) return;
            int next = stack.getDamageValue() + amount;
            if (next >= stack.getMaxDamage()) {
                stack.shrink(1);
            } else {
                stack.setDamageValue(Math.max(0, next));
            }
        };
    }
}
