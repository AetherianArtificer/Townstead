package com.aetherianartificer.townstead.api.v1.event;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** A skill was learned. */
public record VillagerSkillLearnedEvent(
        LivingEntity entity,
        UUID uuid,
        ResourceLocation skillId,
        boolean forced
) implements TownsteadEvent {
}
