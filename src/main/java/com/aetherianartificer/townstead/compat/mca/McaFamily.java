package com.aetherianartificer.townstead.compat.mca;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.FamilyTree;
import net.minecraft.server.level.ServerLevel;

/** MCA's family tree, for villagers who leave the world by a way other than dying. */
public final class McaFamily {
    private McaFamily() {}

    /**
     * Takes a villager who is gone for good off the tree, as MCA does at a death, unless a family
     * remembers them. Removing them from the world leaves their node and generated parents otherwise.
     */
    public static void forget(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        FamilyTree tree = FamilyTree.get(level);
        tree.getOrEmpty(villager.getUUID()).filter(entry -> !entry.willBeRemembered()).ifPresent(entry -> {
            entry.streamParents().forEach(tree::remove);
            tree.remove(villager.getUUID());
        });
    }
}
