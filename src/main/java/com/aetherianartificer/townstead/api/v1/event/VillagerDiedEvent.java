package com.aetherianartificer.townstead.api.v1.event;

import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/** A villager died. {@code cause} is the damage source id. */
public record VillagerDiedEvent(
        LivingEntity villager,
        UUID uuid,
        String cause
) implements TownsteadEvent {
}
