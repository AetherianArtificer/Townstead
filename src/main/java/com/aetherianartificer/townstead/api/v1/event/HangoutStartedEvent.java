package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.HangoutVisitSnapshot;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/** A villager set out for a hangout venue. */
public record HangoutStartedEvent(
        LivingEntity villager,
        UUID uuid,
        HangoutVisitSnapshot visit
) implements TownsteadEvent {
}
