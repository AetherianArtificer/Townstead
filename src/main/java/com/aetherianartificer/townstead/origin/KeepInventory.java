package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.types.KeepInventoryGeneType;
import net.minecraft.world.entity.player.Player;

/**
 * Copies a {@code keep_inventory} race's items onto its respawned body. The death
 * drop was canceled by {@code InventoryKeepInventoryMixin}, so the original (dead)
 * player still holds the items; this transfers them to the clone. Branch-agnostic.
 */
public final class KeepInventory {

    private KeepInventory() {}

    public static void onClone(Player original, Player respawned) {
        if (original == null || respawned == null) return;
        if (ExpressedGenes.instancesOf(original, KeepInventoryGeneType.Instance.class).isEmpty()) return;
        respawned.getInventory().replaceWith(original.getInventory());
    }
}
