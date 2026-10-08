package com.aetherianartificer.townstead.api.v1.event;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** A villager ate or drank. Readings are before and after, on each need's own scale; energy is energy, not fatigue. */
public record VillagerRefueledEvent(
        LivingEntity villager,
        UUID uuid,
        ResourceLocation item,
        int hungerBefore,
        int hungerAfter,
        int thirstBefore,
        int thirstAfter,
        int energyBefore,
        int energyAfter
) implements TownsteadEvent {
}
