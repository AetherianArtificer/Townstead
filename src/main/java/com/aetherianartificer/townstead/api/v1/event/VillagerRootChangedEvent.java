package com.aetherianartificer.townstead.api.v1.event;

import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/**
 * A villager's or player's Root was reassigned: the character editor, the root command, or
 * {@code villagers().setRoot}. Not posted for the root a villager is given at spawn or birth.
 */
public record VillagerRootChangedEvent(
        LivingEntity entity,
        UUID uuid,
        boolean player,
        String rootBefore,
        String rootAfter
) implements TownsteadEvent {
}
