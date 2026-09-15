package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.NeedBand;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.api.v1.model.VillageNeedsSummary;
import net.minecraft.server.MinecraftServer;

/** A village's aggregate reading of one need crossed a band edge. */
public record VillageNeedsBandChangedEvent(
        MinecraftServer server,
        VillageId village,
        String needId,
        NeedBand before,
        NeedBand after,
        VillageNeedsSummary summary
) implements TownsteadEvent {
}
