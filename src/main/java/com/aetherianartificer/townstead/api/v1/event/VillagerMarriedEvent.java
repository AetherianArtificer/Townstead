package com.aetherianartificer.townstead.api.v1.event;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/** A villager married. Posted once per pair per day, from whichever side noticed first. */
public record VillagerMarriedEvent(
        LivingEntity partner,
        UUID uuid,
        Optional<UUID> spouse
) implements TownsteadEvent {
}
