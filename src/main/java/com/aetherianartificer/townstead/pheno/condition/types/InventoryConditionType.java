package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.item.ItemCondition;
import com.aetherianartificer.townstead.pheno.condition.item.ItemConditions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * True when the player carries {@code [min, max]} items matching an {@code item_condition}
 * (Apoli's {@code inventory}). Player-only; counts every matching stack's size.
 *
 * <p>JSON: {@code { "type":"pheno:inventory", "min":8,
 * "item_condition":{ "type":"pheno:ingredient", "tag":"minecraft:coals" } }}</p>
 */
public final class InventoryConditionType implements ConditionType {

    public static final String KEY = "pheno:inventory";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ItemCondition item = ItemConditions.parse(json.get("item_condition"));
        if (item == null) return null;
        int min = GsonHelper.getAsInt(json, "min", 1);
        int max = GsonHelper.getAsInt(json, "max", Integer.MAX_VALUE);
        return ctx -> {
            if (!(ctx.entity() instanceof Player player)) return false;
            int count = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && item.test(ctx.level(), stack)) count += stack.getCount();
            }
            return count >= min && count <= max;
        };
    }
}
