package com.aetherianartificer.townstead.api.v1.event;

import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/** A villager's MCA profession changed. Ids are canonical profession ids. */
public record VillagerProfessionChangedEvent(
        LivingEntity villager,
        UUID uuid,
        String professionBefore,
        String professionAfter
) implements TownsteadEvent {
}
