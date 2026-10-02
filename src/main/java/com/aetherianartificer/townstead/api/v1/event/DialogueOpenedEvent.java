package com.aetherianartificer.townstead.api.v1.event;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** A dialogue opened between a villager and a player. */
public record DialogueOpenedEvent(
        LivingEntity villager,
        UUID uuid,
        ServerPlayer player
) implements TownsteadEvent {
}
