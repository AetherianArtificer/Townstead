package com.aetherianartificer.townstead.api.v1.event;

import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/** A villager was born. */
public record VillagerBornEvent(
        LivingEntity baby,
        UUID uuid
) implements TownsteadEvent {
}
