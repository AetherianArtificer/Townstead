package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

/**
 * Who may have a Root in this world. {@code state} is {@code everyone}, {@code villagers},
 * {@code players}, {@code discoverable} or {@code off} as World Setup sets it. A discoverable Root
 * spawns as villagers, and players can choose it once someone on the server befriends one of them;
 * {@code discovered} says whether that has happened. {@code villagersSpawn} and
 * {@code playersChoose} also account for switched-off species, ancestries, lineages, packs and the
 * config blocklist. {@code spawnRate} is the Root's own rate times every group rate, 1 being normal.
 *
 * <p>Added in API revision 2.</p>
 */
public record RootAccessSnapshot(
        ResourceLocation root,
        String state,
        boolean villagersSpawn,
        boolean playersChoose,
        double spawnRate,
        boolean discovered
) {
}
