package com.aetherianartificer.townstead.work.recipe;

/**
 * The kinds of workstation the work engine knows how to operate. A role says how a block is
 * driven, never who may drive it or what they may make — that is the profession's business.
 */
public enum StationType {
    CUTTING_BOARD,
    HOT_STATION,
    FIRE_STATION,
    PASSIVE_STATION,
    PLACE_SURFACE,
    FURNACE_STATION,
    /**
     * A block that is only a place to work: it holds nothing and processes nothing. The crafter
     * carries the inputs, works at the surface for the recipe's time, and the exchange of real
     * inputs for real output happens in their hands — a crafting table, a stonecutter.
     */
    CRAFT_SURFACE
}
