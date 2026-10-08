package com.aetherianartificer.townstead.hospitality.service;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;

/** Immutable snapshot of one currently actionable service request. */
public record ServiceRequest(ServiceRequestKey key, Authority authority, ExactServiceProduct product,
                             ResourceLocation category, ResourceLocation domain, BlockPos delivery,
                             long deadlineTick, int priority, Map<String, String> metadata) {
    public enum Authority { TOWNSTEAD, FOREIGN }

    public ServiceRequest {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(delivery, "delivery");
        delivery = new BlockPos(delivery.getX(), delivery.getY(), delivery.getZ());
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean expired(long gameTime) { return deadlineTick >= 0 && gameTime > deadlineTick; }
}
