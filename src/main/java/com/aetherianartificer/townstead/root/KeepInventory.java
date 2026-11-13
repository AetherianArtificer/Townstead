package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.gene.types.KeepInventoryGeneType;
import net.minecraft.world.entity.player.Player;

/**
 * Copies a {@code keep_inventory} race's items onto its respawned body. The death
 * drop was canceled by {@code InventoryKeepInventoryMixin}, so the original (dead)
 * player still holds the items; this transfers them to the clone. Branch-agnostic.
 */
public final class KeepInventory {

    private static final java.util.Set<java.util.UUID> KEPT = new java.util.HashSet<>();

    private KeepInventory() {}

    /**
     * True when a keep_inventory power of the dying player holds now. The answer is remembered until
     * the respawn, because a condition (a resource, a place) may read differently on the new body.
     */
    public static boolean keeps(Player dying) {
        for (KeepInventoryGeneType.Instance instance : ExpressedGenes.instancesOf(dying, KeepInventoryGeneType.Instance.class)) {
            if (instance.condition() == null
                    || instance.condition().test(new com.aetherianartificer.townstead.pheno.condition.ConditionContext(dying))) {
                KEPT.add(dying.getUUID());
                return true;
            }
        }
        return false;
    }

    public static void onClone(Player original, Player respawned) {
        if (original == null || respawned == null) return;
        if (!KEPT.remove(original.getUUID())) return;
        respawned.getInventory().replaceWith(original.getInventory());
    }
}
