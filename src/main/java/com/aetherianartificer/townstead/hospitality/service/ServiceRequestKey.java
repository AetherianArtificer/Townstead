package com.aetherianartificer.townstead.hospitality.service;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Stable identity used for idempotency and Townstead-side leases. */
public record ServiceRequestKey(ResourceLocation provider, ResourceLocation dimension,
                                String site, String session, String request) {
    public ServiceRequestKey {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(dimension, "dimension");
        site = requireText(site, "site");
        session = requireText(session, "session");
        request = requireText(request, "request");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
