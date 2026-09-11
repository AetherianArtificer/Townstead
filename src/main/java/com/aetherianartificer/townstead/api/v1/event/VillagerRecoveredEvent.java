package com.aetherianartificer.townstead.api.v1.event;

import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/** A collapsed villager recovered enough energy to stand. */
public record VillagerRecoveredEvent(
        LivingEntity villager,
        UUID uuid,
        int energy
) implements TownsteadEvent {
}
