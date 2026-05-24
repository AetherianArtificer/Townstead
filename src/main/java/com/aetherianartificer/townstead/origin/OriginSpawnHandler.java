package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;

/**
 * Applies origins to villagers at runtime.
 *
 * <p>{@link #onTrueSpawn} runs only for genuinely spawned villagers (driven by
 * the FinalizeSpawn event), so it is safe to roll genes there. Loaded/legacy
 * villagers are handled by {@link #backfillIfMissing}, which assigns the default
 * origin id without touching their already-rolled genes.</p>
 */
public final class OriginSpawnHandler {

    private OriginSpawnHandler() {}

    /** Choose the villager's origin, stamp the id, and constrain genes into its ranges. */
    public static void onTrueSpawn(VillagerEntityMCA villager) {
        // Origin choice is fixed to the default for now; per-world/village biases
        // will resolve the origin id here in a later phase.
        ResourceLocation originId = OriginRegistry.DEFAULT_ID;
        TownsteadVillager state = TownsteadVillagers.get(villager);
        state.life().setOrigin(originId.toString());
        // Clamp (not re-roll) so genes are imported as-is for full-range origins
        // like Overworlder; only narrowed genes are pulled into range.
        OriginGenes.clamp(villager, OriginRegistry.effectiveGenome(originId));
    }

    /** Assign the default origin id to a villager that has none, leaving genes untouched. */
    public static void backfillIfMissing(VillagerEntityMCA villager) {
        TownsteadVillager state = TownsteadVillagers.get(villager);
        if (!state.life().hasOrigin()) {
            state.life().setOrigin(OriginRegistry.DEFAULT_ID.toString());
        }
    }
}
