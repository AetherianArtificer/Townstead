package com.aetherianartificer.townstead.shepherd;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;

/** Inventory predicates for carrying wool to storage. */
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
}
