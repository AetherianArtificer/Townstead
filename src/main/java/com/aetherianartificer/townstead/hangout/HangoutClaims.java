package com.aetherianartificer.townstead.hangout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Atomic, owner-scoped leases for visitors, venue capacity, spots, and linked resources. */
public final class HangoutClaims {
    public record Key(String dimension, String kind, String value) {
        public Key {
            if (dimension == null || dimension.isBlank()) throw new IllegalArgumentException("dimension is required");
            if (kind == null || kind.isBlank()) throw new IllegalArgumentException("kind is required");
            if (value == null || value.isBlank()) throw new IllegalArgumentException("value is required");
        }
    }

    public record Lease(Key key, UUID owner, long expiresAt) {}

    private final Map<Key, Lease> leases = new HashMap<>();

    public synchronized boolean tryClaimAll(UUID owner, Collection<Key> requested, long now, long leaseTicks) {
        Objects.requireNonNull(owner, "owner");
        if (leaseTicks < 1) throw new IllegalArgumentException("leaseTicks must be positive");
        Set<Key> keys = new LinkedHashSet<>(requested == null ? List.of() : requested);
        if (keys.isEmpty()) return false;
        prune(now);
        for (Key key : keys) {
            Lease existing = leases.get(key);
            if (existing != null && !existing.owner().equals(owner)) return false;
        }
        long expiry = now + leaseTicks;
        for (Key key : keys) leases.put(key, new Lease(key, owner, expiry));
        return true;
    }

    public synchronized boolean renew(UUID owner, long now, long leaseTicks) {
        if (leaseTicks < 1) throw new IllegalArgumentException("leaseTicks must be positive");
        prune(now);
        List<Key> owned = new ArrayList<>();
        for (Lease lease : leases.values()) if (lease.owner().equals(owner)) owned.add(lease.key());
        if (owned.isEmpty()) return false;
        long expiry = now + leaseTicks;
        for (Key key : owned) leases.put(key, new Lease(key, owner, expiry));
        return true;
    }

    public synchronized boolean owns(UUID owner, Key key, long now) {
        prune(now);
        Lease lease = leases.get(key);
        return lease != null && lease.owner().equals(owner);
    }

    public synchronized boolean available(Key key, UUID owner, long now) {
        prune(now);
        Lease lease = leases.get(key);
        return lease == null || lease.owner().equals(owner);
    }

    public synchronized void release(UUID owner) {
        leases.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
    }

    public synchronized void releaseKind(UUID owner, String kind) {
        leases.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner)
                && entry.getKey().kind().equals(kind));
    }

    public synchronized void prune(long now) {
        leases.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    public synchronized int size() { return leases.size(); }
}
