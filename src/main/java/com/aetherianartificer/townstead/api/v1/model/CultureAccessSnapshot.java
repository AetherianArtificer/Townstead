package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

/**
 * Whether founders may be raised in a culture in this world, and how often next to the others their
 * Root allows, 1 being normal. Added in API revision 2.
 */
public record CultureAccessSnapshot(
        ResourceLocation culture,
        boolean enabled,
        double spawnRate
) {
}
