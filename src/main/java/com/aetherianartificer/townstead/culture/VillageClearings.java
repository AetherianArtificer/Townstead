package com.aetherianartificer.townstead.culture;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/**
 * Keeps trees from growing through culture villages. While a chunk is decorated, this holds the
 * ground of the culture village buildings and streets near it, widened so a tree's crown planted
 * beside a building does not cover it, and trees whose trunk would stand there do not grow.
 */
public final class VillageClearings {
    /** How far around a building trees keep away, about the reach of a large tree's crown. */
    public static final int BUILDING_MARGIN = 4;
    /** How far around a street. */
    public static final int STREET_MARGIN = 1;
    private static final ThreadLocal<List<BoundingBox>> CLEARINGS = new ThreadLocal<>();

    private VillageClearings() {}

    public static void begin(List<BoundingBox> clearings) {
        if (clearings.isEmpty()) CLEARINGS.remove();
        else CLEARINGS.set(List.copyOf(clearings));
    }

    public static void end() {
        CLEARINGS.remove();
    }

    /** Whether a tree rooted here would grow into a culture village. */
    public static boolean blocks(BlockPos root) {
        List<BoundingBox> clearings = CLEARINGS.get();
        if (clearings == null) return false;
        for (BoundingBox box : clearings) {
            if (root.getX() >= box.minX() && root.getX() <= box.maxX() && root.getZ() >= box.minZ()
                    && root.getZ() <= box.maxZ() && root.getY() >= box.minY() - 8 && root.getY() <= box.maxY() + 8) {
                return true;
            }
        }
        return false;
    }

    /** A box widened on every side but up and down. */
    public static BoundingBox widen(BoundingBox box, int margin) {
        return new BoundingBox(box.minX() - margin, box.minY(), box.minZ() - margin,
                box.maxX() + margin, box.maxY(), box.maxZ() + margin);
    }
}
