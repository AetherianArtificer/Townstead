package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Swaps each armor piece the actor wears with the first piece for the same slot in its inventory.
 * A slot with no spare piece is left alone. Only players carry spare armor, so other actors fail.
 * Fails when nothing was swapped.
 *
 * <p>JSON: {@code { "type":"pheno:swap_armor" }}</p>
 */
public final class SwapArmorActionType implements ActionType {

    public static final String KEY = "pheno:swap_armor";
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> {
            if (!(ctx.entity() instanceof Player player)) {
                ctx.fail();
                return;
            }
            Inventory inventory = player.getInventory();
            boolean swapped = false;
            for (EquipmentSlot slot : SLOTS) {
                for (int i = 0; i < inventory.items.size(); i++) {
                    ItemStack spare = inventory.items.get(i);
                    if (!(spare.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) continue;
                    ItemStack worn = player.getItemBySlot(slot);
                    inventory.items.set(i, worn);
                    player.setItemSlot(slot, spare);
                    swapped = true;
                    break;
                }
            }
            if (!swapped) ctx.fail();
        };
    }
}
