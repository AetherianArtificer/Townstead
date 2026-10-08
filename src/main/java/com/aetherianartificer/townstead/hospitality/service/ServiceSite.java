package com.aetherianartificer.townstead.hospitality.service;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A foreign or Townstead-owned hospitality worksite discovered by a service provider. */
public record ServiceSite(ResourceLocation provider, ResourceLocation dimension, String id,
                          BlockPos anchor, Set<Long> bounds, Map<String, String> metadata) {
    public ServiceSite {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(dimension, "dimension");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        Objects.requireNonNull(anchor, "anchor");
        anchor = new BlockPos(anchor.getX(), anchor.getY(), anchor.getZ());
        bounds = bounds == null ? Set.of() : Set.copyOf(bounds);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
