package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.VillageId;
import net.minecraft.server.level.ServerLevel;

/** A registered building changed type, usually to a higher tier of its family. */
public record BuildingUpgradedEvent(
        ServerLevel level,
        VillageId village,
        int buildingId,
        String typeBefore,
        String typeAfter,
        int tierBefore,
        int tierAfter
) implements TownsteadEvent {
}
