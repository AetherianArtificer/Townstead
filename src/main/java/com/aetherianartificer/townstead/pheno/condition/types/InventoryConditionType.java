package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.item.ItemCondition;
import com.aetherianartificer.townstead.pheno.condition.item.ItemConditions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * True when an inventory-bearing entity carries {@code [min, max]} matching items.
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
            Container inventory;
            if (ctx.entity() instanceof Player player) inventory = player.getInventory();
            else if (ctx.entity() instanceof InventoryCarrier carrier) inventory = carrier.getInventory();
            else return false;
            int count = 0;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty() && item.test(ctx.level(), stack)) count += stack.getCount();
            }
            return count >= min && count <= max;
        };
    }
}
