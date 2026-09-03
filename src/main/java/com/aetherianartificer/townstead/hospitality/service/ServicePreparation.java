package com.aetherianartificer.townstead.hospitality.service;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Objects;

/** One provider-owned workstation able to turn an offered product into a deliverable serving. */
public record ServicePreparation(ServiceRequestKey request, BlockPos position,
                                 Map<String, String> metadata) {
    public ServicePreparation {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(position, "position");
        position = new BlockPos(position.getX(), position.getY(), position.getZ());
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
