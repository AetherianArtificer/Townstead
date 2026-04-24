package com.aetherianartificer.townstead.enclosure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Map;
import java.util.Set;

/**
 * A detected enclosed area — the horizontal interior reachable from the
 * player's position through passable blocks, the ring of perimeter blocks
 * (fences, gates, walls, or solid) that contained the flood-fill, and a
 * tally of block ids found inside the interior volume (interior tiles +
 * a few blocks above, for hay bales, blood grates, lanterns placed in
 * the pen).
 *
 * <p>The enclosure itself is type-agnostic. Classification against
 * registered enclosure building types happens in {@code EnclosureClassifier}
 * using the {@link #interiorContent} tally and the perimeter counts in
 * {@link #perimeter}.
 */
public record Enclosure(
        BoundingBox bounds,
        Set<BlockPos> interior,
        Set<BlockPos> perimeter,
        int fenceCount,
        int fenceGateCount,
        int wallCount,
        Map<String, Integer> interiorContent) {

    public int interiorSize() {
        return interior.size();
    }
}
