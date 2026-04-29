package com.aetherianartificer.townstead.shepherd;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Shared inventory predicates for the shepherd loop. The shear and deposit
 * tasks both need to ask "does this villager have wool" / "is there room
 * to keep shearing"; consolidating those reads keeps the two behaviors
 * in agreement about when one should yield to the other.
 */
public final class ShepherdInventory {
    private ShepherdInventory() {}

    /** True when the villager has at least one wool stack to deposit. */
    public static boolean hasWool(VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ItemTags.WOOL)) return true;
        }
        return false;
    }

    /**
     * True if there's at least one slot that can absorb more wool — either
     * empty, or holding wool with capacity remaining. When false the
     * shepherd should yield from shearing and let the deposit task run.
     */
    public static boolean hasRoomForWool(VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) return true;
            if (s.is(ItemTags.WOOL) && s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }
}
