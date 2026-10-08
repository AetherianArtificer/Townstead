package com.aetherianartificer.townstead.api.v1.event;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Two villagers finished a conversation on a topic with an outcome. */
public record ConversationHeldEvent(
        LivingEntity initiator,
        UUID initiatorUuid,
        LivingEntity responder,
        UUID responderUuid,
        ResourceLocation topic,
        String outcome
) implements TownsteadEvent {
}
