package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.VillageId;
import net.minecraft.server.level.ServerLevel;

/** A building appeared in a village's register. */
public record BuildingEstablishedEvent(
        ServerLevel level,
        VillageId village,
        int buildingId,
        String type,
        String family,
        int tier
) implements TownsteadEvent {
}
