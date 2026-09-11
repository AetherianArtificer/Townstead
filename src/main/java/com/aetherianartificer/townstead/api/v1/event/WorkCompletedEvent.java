package com.aetherianartificer.townstead.api.v1.event;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** A worker finished a unit of work and its career took the XP. {@code verb} is the chronicle verb ({@code townstead:cooked}), {@code objectId} what was made when known. */
public record WorkCompletedEvent(
        LivingEntity worker,
        UUID uuid,
        String professionId,
        String verb,
        Optional<ResourceLocation> objectId,
        float magnitude,
        int appliedXp,
        int tierBefore,
        int tierAfter
) implements TownsteadEvent {
}
