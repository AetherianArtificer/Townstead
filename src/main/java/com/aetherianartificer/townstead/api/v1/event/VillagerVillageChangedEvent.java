package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.VillageId;
import java.util.Optional;
import java.util.UUID;

/** A villager's home village changed, as seen by the resident register. */
public record VillagerVillageChangedEvent(
        UUID uuid,
        String name,
        Optional<VillageId> before,
        VillageId after
) implements TownsteadEvent {
}
