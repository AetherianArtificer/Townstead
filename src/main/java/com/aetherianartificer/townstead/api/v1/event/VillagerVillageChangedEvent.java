package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.VillageId;
import java.util.Optional;
import java.util.UUID;

/** A villager claimed a home in a new village, including the first home it is seen with. */
public record VillagerVillageChangedEvent(
        UUID uuid,
        String name,
        Optional<VillageId> before,
        VillageId after
) implements TownsteadEvent {
}
