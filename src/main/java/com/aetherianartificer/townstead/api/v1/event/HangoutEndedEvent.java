package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.HangoutVisitSnapshot;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/** A hangout visit ended, successfully or not. The villager may be unloaded, in which case it is empty. */
public record HangoutEndedEvent(
        Optional<LivingEntity> villager,
        UUID uuid,
        HangoutVisitSnapshot visit,
        boolean success
) implements TownsteadEvent {
}
