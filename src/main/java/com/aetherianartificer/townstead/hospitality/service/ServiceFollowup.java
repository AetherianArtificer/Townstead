package com.aetherianartificer.townstead.hospitality.service;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;

/** Provider-owned post-service work such as clearing, returning, or washing a native dish. */
public record ServiceFollowup(ServiceRequestKey request, ResourceLocation type, BlockPos position,
                              Map<String, String> metadata) {
    public ServiceFollowup {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");
        position = new BlockPos(position.getX(), position.getY(), position.getZ());
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
