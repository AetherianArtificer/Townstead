package com.aetherianartificer.townstead.dock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * A registered dock, read back from its MCA building for the fisherman. Docks are ordinary
 * open-air buildings ({@code dock_l1..3}): MCA counts the furniture and
 * {@code SiteRequirements} checks the deck stands over water.
 *
 * plankCount is the building's footprint size, retained for debug output.
 */
public record Dock(BoundingBox bounds, int plankCount, int tier) {
    public boolean contains(BlockPos pos) {
        return pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX()
                && pos.getY() >= bounds.minY() && pos.getY() <= bounds.maxY()
                && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ();
    }

    /**
     * Horizontal centroid of the dock bounds, Y-anchored just above the deck
     * so audio-visual recognition effects hover over the pier rather than
     * under it.
     */
    public Vec3 centerVec() {
        double cx = (bounds.minX() + bounds.maxX() + 1) / 2.0;
        double cz = (bounds.minZ() + bounds.maxZ() + 1) / 2.0;
        return new Vec3(cx, bounds.maxY() - 1.0, cz);
    }
}
