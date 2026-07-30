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
    FURNACE_STATION
}
