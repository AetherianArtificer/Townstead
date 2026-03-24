package com.aetherianartificer.townstead.ai.work;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

public record WorkTarget(
        Type type,
        BlockPos pos,
        @Nullable BlockPos anchor,
        String label
) {
    public enum Type {
        BUILDING_APPROACH,
        BUILDING_ENTRY,
        STATION_STAND,
        ZONE_POINT
    }

    public static WorkTarget buildingApproach(BlockPos pos, @Nullable BlockPos anchor, String label) {
        return new WorkTarget(Type.BUILDING_APPROACH, pos.immutable(), anchor == null ? null : anchor.immutable(), label);
    }

    public static WorkTarget buildingEntry(BlockPos pos, @Nullable BlockPos anchor, String label) {
        return new WorkTarget(Type.BUILDING_ENTRY, pos.immutable(), anchor == null ? null : anchor.immutable(), label);
    }

    public static WorkTarget stationStand(BlockPos pos, @Nullable BlockPos anchor, String label) {
        return new WorkTarget(Type.STATION_STAND, pos.immutable(), anchor == null ? null : anchor.immutable(), label);
    }

    public static WorkTarget zonePoint(BlockPos pos, @Nullable BlockPos anchor, String label) {
        return new WorkTarget(Type.ZONE_POINT, pos.immutable(), anchor == null ? null : anchor.immutable(), label);
    }

    public String describe() {
        String anchorDesc = anchor == null ? "none" : anchor.getX() + "," + anchor.getY() + "," + anchor.getZ();
        return type.name().toLowerCase() + ":" + label + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "#" + anchorDesc;
    }
}
