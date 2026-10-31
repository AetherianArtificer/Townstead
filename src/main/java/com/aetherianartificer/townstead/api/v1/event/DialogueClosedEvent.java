package com.aetherianartificer.townstead.api.v1.event;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** A dialogue closed. {@code heartDelta} is how the relationship moved while it was open, when known. */
public record DialogueClosedEvent(
        LivingEntity villager,
        UUID uuid,
        ServerPlayer player,
        int heartDelta
) implements TownsteadEvent {
}
