package com.aetherianartificer.townstead.culture;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Whether the ground under a planned village building can hold it. A building's foundation reaches a
 * few blocks down to the ground ({@link VillageFoundations}), but not across a lake or down a cliff,
 * where it would stand on nothing. A building is sampled at nine points of its footprint; one standing over water at most of
 * them, or high above the ground at most of them, is left out of the village, leaving an empty lot.
 * Buildings meant to stand in water, such as a fisherman's stilt hut, may stand over water.
 */
public final class VillageGround {
    /** How far below a building the ground may lie: its foundation reaches that far. */
    static final int REACH = VillageFoundations.DEPTH;
    /** How many of the nine sample points may be bad before the building is left out. */
    static final int TOLERATED = 4;

    private VillageGround() {}

    /** The ground of a world at a column: the first free block, and the first free block of solid ground. */
    public interface Heights {
        int surface(int x, int z);

        int floor(int x, int z);
    }

    public static boolean fits(BoundingBox box, boolean wet, Heights heights) {
        int water = 0, drop = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int x = sample(box.minX(), box.maxX(), i), z = sample(box.minZ(), box.maxZ(), j);
                int floor = heights.floor(x, z);
                if (heights.surface(x, z) > floor) {
                    if (!wet) water++;
                } else if (box.minY() - floor > REACH) {
                    drop++;
                }
            }
        }
        return water <= TOLERATED && drop <= TOLERATED;
    }

    /** One of three sample points across a span, kept a block inside its edges. */
    private static int sample(int min, int max, int index) {
        if (max - min < 2) return (min + max) / 2;
        return switch (index) {
            case 0 -> min + 1;
            case 1 -> (min + max) / 2;
            default -> max - 1;
        };
    }
}
