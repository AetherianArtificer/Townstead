package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.SpiritSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import net.minecraft.server.level.ServerLevel;

/** A village's spirit readout changed shape: classification, tier, or dominant spirit. */
public record VillageSpiritChangedEvent(
        ServerLevel level,
        VillageId village,
        SpiritSnapshot before,
        SpiritSnapshot after
) implements TownsteadEvent {
}
