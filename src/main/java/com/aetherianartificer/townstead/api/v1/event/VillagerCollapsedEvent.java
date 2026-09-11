package com.aetherianartificer.townstead.api.v1.event;

import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/** A villager's energy hit the floor and it collapsed. */
public record VillagerCollapsedEvent(
        LivingEntity villager,
        UUID uuid,
        int energy
) implements TownsteadEvent {
}
