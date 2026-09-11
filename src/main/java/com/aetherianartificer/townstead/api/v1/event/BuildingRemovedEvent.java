package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.VillageId;
import net.minecraft.server.level.ServerLevel;

/** A building left a village's register. */
public record BuildingRemovedEvent(
        ServerLevel level,
        VillageId village,
        int buildingId,
        String type
) implements TownsteadEvent {
}
