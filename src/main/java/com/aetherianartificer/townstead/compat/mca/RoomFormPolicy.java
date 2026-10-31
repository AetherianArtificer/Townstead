package com.aetherianartificer.townstead.compat.mca;

import com.aetherianartificer.townstead.recognition.BuildingEnclosurePolicies;
import net.conczin.mca.resources.data.BuildingType;

import java.util.List;

/**
 * Keeps open-air-only types ({@code "enclosure": "none"}) out of MCA's room matching. MCA ranks a
 * type by its blocks alone, so a pen that needs one fence outranked a house as soon as a room held
 * a fence-post table: a room's walls and door keep an animal in as well as any paddock.
 */
public final class RoomFormPolicy {
    private RoomFormPolicy() {}

    public static List<BuildingType> filter(List<BuildingType> candidates) {
        if (candidates == null || candidates.isEmpty()) return candidates;
        if (candidates.stream().allMatch(RoomFormPolicy::allowsRoom)) return candidates;
        return candidates.stream().filter(RoomFormPolicy::allowsRoom).toList();
    }

    private static boolean allowsRoom(BuildingType type) {
        return BuildingEnclosurePolicies.modeOf(type.name()).allowsRoom();
    }
}
