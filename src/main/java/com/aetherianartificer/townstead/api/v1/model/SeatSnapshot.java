package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The building a political actor governs from: a Seat of Power for a polity, a Headquarters for
 * an organization. {@code buildingType} is the host's MCA building type, empty when the building
 * is gone. The tier and {@code functions} come from that building type; a building type with no
 * Seat data gives a Meeting Place. A damaged Seat names its {@code damage}
 * ({@code building_missing} or {@code lectern_missing}) and lists no functions until repaired.
 */
public record SeatSnapshot(PoliticalActorRef actor,
                           VillageId settlement,
                           BlockPos lectern,
                           int buildingId,
                           String buildingType,
                           int tier,
                           List<ResourceLocation> functions,
                           long designatedAt,
                           String damage) {
    public SeatSnapshot {
        buildingType = buildingType == null ? "" : buildingType;
        functions = List.copyOf(functions);
        damage = damage == null ? "" : damage;
    }

    public boolean damaged() {
        return !damage.isEmpty();
    }
}
