package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.item.ItemAction;
import com.aetherianartificer.townstead.pheno.action.item.ItemActionContext;
import com.aetherianartificer.townstead.pheno.action.item.ItemActions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Bridges an entity context to an item action: runs the wrapped {@code item_action} on
 * the actor's equipped item in {@code slot} (Apoli's entity {@code equipped_item_action}).
 * {@code slot} is {@code mainhand} (default), {@code offhand}, {@code head}, {@code chest},
 * {@code legs} or {@code feet}.
 *
 * <p>JSON: {@code { "type":"pheno:equipped_item_action", "slot":"mainhand",
 * "item_action":{ "type":"pheno:damage", "amount":1 } }}</p>
 */
public final class EquippedItemActionType implements ActionType {

    public static final String KEY = "pheno:equipped_item_action";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        EquipmentSlot slot = parseSlot(GsonHelper.getAsString(json, "slot", "mainhand"));
        ItemAction itemAction = ItemActions.parse(json.get("item_action"));
        if (itemAction == null) return null;
        return ctx -> {
            ItemStack stack = ctx.entity().getItemBySlot(slot);
            if (stack.isEmpty()) return;
            itemAction.run(new ItemActionContext(stack, ctx.entity()));
            ctx.entity().setItemSlot(slot, stack);
        };
    }

    private static EquipmentSlot parseSlot(String raw) {
        try {
            return EquipmentSlot.byName(raw.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return EquipmentSlot.MAINHAND;
        }
    }
}
