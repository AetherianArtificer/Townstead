package com.aetherianartificer.townstead.hangout;

import com.aetherianartificer.townstead.performance.PerformanceHandle;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;

/** A bounded social moment assembled from visitors who are already present. */
final class HangoutBeat {
    private final UUID id;
    private final ResourceLocation venueDefinition;
    private final int buildingId;
    private final ResourceLocation activity;
    private final Map<UUID, String> roles;
    private final Map<UUID, PerformanceHandle> performances = new LinkedHashMap<>();
    private final Set<UUID> simpleServiceAttempted = new LinkedHashSet<>();
    private final long startedAt;
    private final long deadline;
    private long lastTick = Long.MIN_VALUE;
    private long nextDialogueAt = Long.MIN_VALUE;
    private long nextExpressionAt = Long.MIN_VALUE;
    private int dialogueCursor;
    private int expressionCursor;

    HangoutBeat(UUID id, ResourceLocation venueDefinition, int buildingId,
                ResourceLocation activity, Map<UUID, String> roles,
                long startedAt, long deadline) {
        this.id = id;
        this.venueDefinition = venueDefinition;
        this.buildingId = buildingId;
        this.activity = activity;
        this.roles = new LinkedHashMap<>(roles);
        this.startedAt = startedAt;
        this.deadline = deadline;
    }

    UUID id() { return id; }
    ResourceLocation venueDefinition() { return venueDefinition; }
    int buildingId() { return buildingId; }
    ResourceLocation activity() { return activity; }
    Map<UUID, String> roles() { return Map.copyOf(roles); }
    Map<UUID, String> mutableRoles() { return roles; }
    Map<UUID, PerformanceHandle> performances() { return performances; }
    boolean simpleServiceAttempted(UUID visitor) { return simpleServiceAttempted.contains(visitor); }
    void markSimpleServiceAttempted(UUID visitor) { simpleServiceAttempted.add(visitor); }
    long startedAt() { return startedAt; }
    long deadline() { return deadline; }
    long lastTick() { return lastTick; }
    void markTick(long tick) { lastTick = tick; }

    boolean dialogueDue(long now, int interval) {
        if (nextDialogueAt == Long.MIN_VALUE) nextDialogueAt = startedAt + Math.max(20, interval / 2);
        if (now < nextDialogueAt) return false;
        nextDialogueAt = now + interval;
        return true;
    }

    boolean expressionDue(long now, int interval) {
        if (nextExpressionAt == Long.MIN_VALUE) nextExpressionAt = startedAt + Math.max(20, interval / 3);
        if (now < nextExpressionAt) return false;
        nextExpressionAt = now + interval;
        return true;
    }

    int nextDialogueIndex(int size) { return Math.floorMod(dialogueCursor++, Math.max(1, size)); }
    int nextExpressionIndex(int size) { return Math.floorMod(expressionCursor++, Math.max(1, size)); }
}
