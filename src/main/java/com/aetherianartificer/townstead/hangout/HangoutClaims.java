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

/** Atomic, session-owned leases for participants, venue capacity, spots, and linked resources. */
public final class HangoutClaims {
    public record Key(String dimension, String kind, String value) {
        public Key {
            if (dimension == null || dimension.isBlank()) throw new IllegalArgumentException("dimension is required");
            if (kind == null || kind.isBlank()) throw new IllegalArgumentException("kind is required");
            if (value == null || value.isBlank()) throw new IllegalArgumentException("value is required");
        }
    }

    public record Lease(Key key, UUID session, long expiresAt) {}

    private final Map<Key, Lease> leases = new HashMap<>();

    public synchronized boolean tryClaimAll(UUID session, Collection<Key> requested, long now, long leaseTicks) {
        Objects.requireNonNull(session, "session");
        if (leaseTicks < 1) throw new IllegalArgumentException("leaseTicks must be positive");
        Set<Key> keys = new LinkedHashSet<>(requested == null ? List.of() : requested);
        if (keys.isEmpty()) return false;
        prune(now);
        for (Key key : keys) {
            Lease existing = leases.get(key);
            if (existing != null && !existing.session().equals(session)) return false;
        }
        long expiry = now + leaseTicks;
        for (Key key : keys) leases.put(key, new Lease(key, session, expiry));
        return true;
    }

    public synchronized boolean renew(UUID session, long now, long leaseTicks) {
        if (leaseTicks < 1) throw new IllegalArgumentException("leaseTicks must be positive");
        prune(now);
        List<Key> owned = new ArrayList<>();
        for (Lease lease : leases.values()) if (lease.session().equals(session)) owned.add(lease.key());
        if (owned.isEmpty()) return false;
        long expiry = now + leaseTicks;
        for (Key key : owned) leases.put(key, new Lease(key, session, expiry));
        return true;
    }

    public synchronized boolean owns(UUID session, Key key, long now) {
        prune(now);
        Lease lease = leases.get(key);
        return lease != null && lease.session().equals(session);
    }

    public synchronized boolean available(Key key, UUID session, long now) {
        prune(now);
        Lease lease = leases.get(key);
        return lease == null || lease.session().equals(session);
    }

    public synchronized void release(UUID session) {
        leases.entrySet().removeIf(entry -> entry.getValue().session().equals(session));
    }

    public synchronized void prune(long now) {
        leases.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    public synchronized int size() { return leases.size(); }
}
