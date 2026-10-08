package com.aetherianartificer.townstead.root.ability;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A power component that directly consumes a resource meter. The HUD uses this small,
 * source-neutral contract to decide whether a shared meter is relevant: a Root ability,
 * career skill, or future power source can all make the same baseline resource visible.
 */
public interface ResourceConsumer {

    /** The consumed resource, or {@code null} when this component has no configured cost. */
    @Nullable ResourceLocation costResource();
}
