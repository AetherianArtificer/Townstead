package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.gene.Gene;
import com.aetherianartificer.townstead.root.gene.types.StartingEquipmentGeneType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Grants a player the items from their expressed {@code starting_equipment} genes,
 * once per gene (tracked in {@link PlayerRoot}). Run on login and when the origin
 * changes; a slotted item is equipped if that slot is free, else added to inventory.
 */
public final class StartingEquipment {

    private StartingEquipment() {}

    public static void grant(ServerPlayer player) {
        for (var gene : com.aetherianartificer.townstead.pheno.power.Powers.active(player)) {
            if (!(gene.component() instanceof StartingEquipmentGeneType.Instance instance)) continue;
            String geneId = gene.id().toString();
            if (PlayerRoot.hasGrantedStarting(player, geneId)) continue;
            for (StartingEquipmentGeneType.Entry entry : instance.items()) {
                Item item = BuiltInRegistries.ITEM.get(entry.item());
                if (item == null) continue;
                ItemStack stack = new ItemStack(item, entry.count());
                if (entry.slot() != null && player.getItemBySlot(entry.slot()).isEmpty()) {
                    player.setItemSlot(entry.slot(), stack);
                } else if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
            PlayerRoot.markGrantedStarting(player, geneId);
        }
    }
}
