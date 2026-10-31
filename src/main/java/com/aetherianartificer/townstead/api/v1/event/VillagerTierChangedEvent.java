package com.aetherianartificer.townstead.api.v1.event;

import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/** A worker reached a new profession tier, through work or an API award. */
public record VillagerTierChangedEvent(
        LivingEntity worker,
        UUID uuid,
        String professionId,
        int tierBefore,
        int tierAfter,
        int appliedXp
) implements TownsteadEvent {
}
