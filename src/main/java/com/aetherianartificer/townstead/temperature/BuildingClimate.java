package com.aetherianartificer.townstead.temperature;

/** Lifecycle entry point retained for temperature settings and server shutdown. */
public final class BuildingClimate {
    private BuildingClimate() {}
    public static void clear() { RoomHeat.clear(); }
}
