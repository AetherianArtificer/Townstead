package com.aetherianartificer.townstead.hospitality.service;

import java.util.Objects;
import java.util.UUID;

/** Recoverable lease for semantic hospitality labor before a concrete item request exists. */
public record ServiceLaborClaim(ServiceRequestKey request, String role, UUID worker, long expiresAtTick) {
    public ServiceLaborClaim {
        Objects.requireNonNull(request, "request");
        if (role == null || role.isBlank()) throw new IllegalArgumentException("role is required");
        Objects.requireNonNull(worker, "worker");
    }

    public boolean expired(long gameTime) { return gameTime >= expiresAtTick; }
}
