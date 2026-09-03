package com.aetherianartificer.townstead.food;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared accounting for bounded multi-use servings that remain placed in the world.
 *
 * <p>A lease reserves portions but does not remove them. The real source commits first; only a
 * successful (or ambiguous post-call exception) commit decrements the ledger. Refusal preserves
 * the reservation. Releasing or expiring a lease returns its unconsumed reservation to the same
 * source, so interruption cannot duplicate or strand a drink.</p>
 */
public final class PlacedServingLeaseLedger {
    public static final int MAX_PORTIONS = 64;
    public static final int MAX_LEASE_TICKS = 20 * 60 * 10;

    public record SourceKey(String dimension, long blockPos, String channel) {
        public SourceKey {
            if (dimension == null || dimension.isBlank() || channel == null || channel.isBlank()) {
                throw new IllegalArgumentException("placed serving source needs dimension and channel");
            }
        }
    }

    public record Lease(UUID id, SourceKey source, UUID owner, int reserved, long expiresAt) {}

    public enum Commit { COMMITTED, REFUSED }
    public enum Status { COMMITTED, COMMITTED_ERROR, REFUSED, DUPLICATE, EXPIRED, UNKNOWN_LEASE }

    @FunctionalInterface
    public interface SourceCommit { Commit commit(int portions) throws Exception; }

    public record ConsumeResult(Status status, int consumed, int leaseRemaining,
                                int sourceRemaining, String detail) {}

    public record SourceSnapshot(int initial, int remaining, int available, int reserved,
                                 int consumed, int activeLeases) {
        public boolean conserved() {
            return initial == remaining + consumed && remaining == available + reserved;
        }
    }

    private static final class SourceState {
        final int initial;
        int remaining;
        int available;
        int consumed;

        SourceState(int portions) {
            initial = portions;
            remaining = portions;
            available = portions;
        }
    }

    private static final class LeaseState {
        final UUID id;
        final SourceKey source;
        final UUID owner;
        int remaining;
        long expiresAt;
        final Map<UUID, ConsumeResult> operations = new LinkedHashMap<>();

        LeaseState(UUID id, SourceKey source, UUID owner, int remaining, long expiresAt) {
            this.id = id;
            this.source = source;
            this.owner = owner;
            this.remaining = remaining;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<SourceKey, SourceState> sources = new HashMap<>();
    private final Map<UUID, LeaseState> leases = new HashMap<>();

    /** Opens or reconciles a source before any patron reserves it. */
    public synchronized boolean open(SourceKey source, int portions) {
        if (source == null || portions < 1 || portions > MAX_PORTIONS) return false;
        SourceState current = sources.get(source);
        if (current == null) {
            sources.put(source, new SourceState(portions));
            return true;
        }
        return current.remaining == portions;
    }

    public synchronized @Nullable Lease acquire(SourceKey source, UUID owner, int requested,
                                                int ttlTicks, long now) {
        if (source == null || owner == null || requested < 1 || requested > MAX_PORTIONS
                || ttlTicks < 1 || ttlTicks > MAX_LEASE_TICKS) return null;
        cleanup(now);
        SourceState state = sources.get(source);
        if (state == null || state.available <= 0) return null;
        int granted = Math.min(requested, state.available);
        state.available -= granted;
        UUID id = UUID.randomUUID();
        LeaseState lease = new LeaseState(id, source, owner, granted, now + ttlTicks);
        leases.put(id, lease);
        return snapshot(lease);
    }

    public synchronized ConsumeResult consume(UUID leaseId, UUID operationId, int portions,
                                              long now, SourceCommit commit) {
        if (leaseId == null || operationId == null || portions < 1 || portions > MAX_PORTIONS
                || commit == null) {
            return new ConsumeResult(Status.UNKNOWN_LEASE, 0, 0, 0, "invalid_request");
        }
        LeaseState lease = leases.get(leaseId);
        if (lease == null) return new ConsumeResult(Status.UNKNOWN_LEASE, 0, 0, 0, "unknown_lease");
        ConsumeResult replay = lease.operations.get(operationId);
        if (replay != null) return new ConsumeResult(Status.DUPLICATE, replay.consumed(),
                replay.leaseRemaining(), replay.sourceRemaining(), replay.detail());
        if (now > lease.expiresAt) {
            releaseInternal(lease);
            return new ConsumeResult(Status.EXPIRED, 0, 0,
                    remaining(lease.source), "lease_expired");
        }
        int amount = Math.min(portions, lease.remaining);
        if (amount <= 0) return remember(lease, operationId,
                new ConsumeResult(Status.REFUSED, 0, 0, remaining(lease.source), "lease_empty"));

        Commit outcome;
        try {
            outcome = commit.commit(amount);
        } catch (Exception failure) {
            // The source may have committed before throwing. Conservatively burn the lease units
            // so a retry cannot obtain a second serving from an ambiguous native interaction.
            applyCommit(lease, amount);
            return remember(lease, operationId, new ConsumeResult(Status.COMMITTED_ERROR, amount,
                    lease.remaining, remaining(lease.source), failure.getClass().getSimpleName()));
        }
        if (outcome != Commit.COMMITTED) {
            return remember(lease, operationId, new ConsumeResult(Status.REFUSED, 0,
                    lease.remaining, remaining(lease.source), "source_refused"));
        }
        applyCommit(lease, amount);
        return remember(lease, operationId, new ConsumeResult(Status.COMMITTED, amount,
                lease.remaining, remaining(lease.source), "source_committed"));
    }

    /** Releases only the unconsumed reservation; committed source portions never reappear. */
    public synchronized boolean release(UUID leaseId) {
        LeaseState lease = leases.get(leaseId);
        if (lease == null) return false;
        releaseInternal(lease);
        return true;
    }

    /** Interruption/reload cleanup, deterministic by expiry then id. */
    public synchronized int cleanup(long now) {
        List<LeaseState> expired = leases.values().stream()
                .filter(lease -> now > lease.expiresAt)
                .sorted(Comparator.comparingLong((LeaseState lease) -> lease.expiresAt)
                        .thenComparing(lease -> lease.id))
                .toList();
        expired.forEach(this::releaseInternal);
        return expired.size();
    }

    public synchronized @Nullable SourceSnapshot snapshot(SourceKey source) {
        SourceState state = sources.get(source);
        if (state == null) return null;
        int reserved = leases.values().stream().filter(lease -> lease.source.equals(source))
                .mapToInt(lease -> lease.remaining).sum();
        long count = leases.values().stream().filter(lease -> lease.source.equals(source)).count();
        return new SourceSnapshot(state.initial, state.remaining, state.available, reserved,
                state.consumed, (int) count);
    }

    public synchronized List<Lease> active() {
        List<Lease> result = new ArrayList<>();
        leases.values().stream().sorted(Comparator.comparing(lease -> lease.id))
                .forEach(lease -> result.add(snapshot(lease)));
        return List.copyOf(result);
    }

    private void applyCommit(LeaseState lease, int amount) {
        SourceState source = sources.get(lease.source);
        if (source == null || source.remaining < amount || lease.remaining < amount) {
            throw new IllegalStateException("placed serving conservation violated");
        }
        lease.remaining -= amount;
        source.remaining -= amount;
        source.consumed += amount;
    }

    private void releaseInternal(LeaseState lease) {
        if (!leases.remove(lease.id, lease)) return;
        SourceState source = sources.get(lease.source);
        if (source != null) source.available += lease.remaining;
        lease.remaining = 0;
    }

    private ConsumeResult remember(LeaseState lease, UUID operationId, ConsumeResult result) {
        lease.operations.put(operationId, result);
        return result;
    }

    private int remaining(SourceKey source) {
        SourceState state = sources.get(source);
        return state == null ? 0 : state.remaining;
    }

    private static Lease snapshot(LeaseState lease) {
        return new Lease(lease.id, lease.source, lease.owner, lease.remaining, lease.expiresAt);
    }
}
