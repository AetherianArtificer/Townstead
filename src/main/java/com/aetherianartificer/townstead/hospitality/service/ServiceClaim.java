package com.aetherianartificer.townstead.hospitality.service;

import java.util.Objects;
import java.util.UUID;

/** A bounded Townstead-side lease; the provider remains authoritative for request validity. */
public record ServiceClaim(ServiceRequest request, UUID worker, long expiresAtTick) {
    public ServiceClaim {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(worker, "worker");
    }

    public boolean expired(long gameTime) { return gameTime > expiresAtTick; }
}
