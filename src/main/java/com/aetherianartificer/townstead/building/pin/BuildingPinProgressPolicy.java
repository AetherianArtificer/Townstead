package com.aetherianartificer.townstead.building.pin;

/** Defines which blocks satisfy a pinned MCA building requirement. */
public final class BuildingPinProgressPolicy {
    private BuildingPinProgressPolicy() {}

    /**
     * MCA matches a building from blocks placed inside its room footprint. Carried blocks are useful
     * shopping information, but they are not building progress and must not satisfy the requirement.
     */
    public static int countedBlocks(int placed, int inventory) {
        return Math.max(0, placed);
    }
}
