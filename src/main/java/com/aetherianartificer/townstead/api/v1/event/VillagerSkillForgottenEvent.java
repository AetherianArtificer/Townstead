package com.aetherianartificer.townstead.api.v1.event;

import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** A skill was removed. {@code removed} includes every skill the removal cascaded to. */
public record VillagerSkillForgottenEvent(
        LivingEntity entity,
        UUID uuid,
        ResourceLocation skillId,
        Set<ResourceLocation> removed,
        boolean forced
) implements TownsteadEvent {
}
