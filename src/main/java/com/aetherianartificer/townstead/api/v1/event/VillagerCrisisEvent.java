package com.aetherianartificer.townstead.api.v1.event;

import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/** A villager entered a survival crisis. {@code kind} is the tap key: {@code townstead:starving}, {@code townstead:freezing}, {@code townstead:sweltering}, {@code townstead:cured}; new kinds may appear. */
public record VillagerCrisisEvent(
        LivingEntity villager,
        UUID uuid,
        String kind
) implements TownsteadEvent {
}
