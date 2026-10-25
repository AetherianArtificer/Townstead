package com.aetherianartificer.townstead.villager;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;

/** What settles when a villager arrives in the world: their state, traits, culture and name. */
public final class VillagerArrival {

    private VillagerArrival() {}

    public static void settle(ServerLevel level, VillagerEntityMCA villager) {
        TownsteadVillagerState.root(villager);
        com.aetherianartificer.townstead.root.trait.TraitBridge.migrate(villager);
        com.aetherianartificer.townstead.root.trait.TraitBlocks.apply(villager);
        // Names settle when a villager arrives, not on a timer: this is the
        // first moment their household and village are resolvable, and it is
        // also what hands the composed name to whatever else displays it.
        // Both naming axes settle before the name is composed. Culture is the
        // sparse one and most villagers have none; the tradition is universal and
        // is what a surname is actually built from, so without it a villager who
        // predates this would publish an empty family name forever.
        com.aetherianartificer.townstead.culture.CultureAssignment.ensure(level, villager);
        com.aetherianartificer.townstead.naming.TraditionAssignment.ensure(level, villager);
        com.aetherianartificer.townstead.naming.VillagerNames.publish(villager);
        villager.getResidency().getHomeVillage().ifPresent(village ->
                com.aetherianartificer.townstead.politics.state.PoliticalVillageBootstrap
                        .ensure(level, village));
    }
}
