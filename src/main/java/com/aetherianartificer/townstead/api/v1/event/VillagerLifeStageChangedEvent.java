package com.aetherianartificer.townstead.api.v1.event;

import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/** A villager moved to another life stage of its Root. */
public record VillagerLifeStageChangedEvent(
        LivingEntity villager,
        UUID uuid,
        String stageBefore,
        String stageAfter,
        boolean senior
) implements TownsteadEvent {
}
