package com.aetherianartificer.townstead.hangout;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Shared state for a group: every claim and embodied handle is released through this owner. */
public final class HangoutSession {
    public enum Phase { TRAVELING, ACTIVE, LEAVING, COMPLETE, INTERRUPTED }

    public record Participant(UUID entity, String role, BlockPos spot,
                              ResourceLocation posture, ResourceLocation adapter,
                              Vec3 embodimentPosition,
                              @Nullable HangoutSpot.RestBonus rest,
                              @Nullable HangoutEmbodiment.Handle handle,
                              boolean serviceAttempted) {
        Participant withHandle(HangoutEmbodiment.Handle next) {
            return new Participant(entity, role, spot, posture, adapter, embodimentPosition,
                    rest, next, serviceAttempted);
        }
        Participant markServiceAttempted() {
            return new Participant(entity, role, spot, posture, adapter, embodimentPosition,
                    rest, handle, true);
        }
    }

    private final UUID id;
    private final ResourceLocation dimension;
    private final ResourceLocation venueDefinition;
    private final int buildingId;
    private final ResourceLocation activity;
    private final ResourceLocation policy;
    private final BlockPos venueAnchor;
    private final Map<UUID, Participant> participants;
    private Phase phase;
    private final long createdAt;
    private long deadline;
    private long activeAt;
    private long lastTick = Long.MIN_VALUE;
    private String exitReason = "";

    HangoutSession(UUID id, ResourceLocation dimension, ResourceLocation venueDefinition, int buildingId,
                   ResourceLocation activity, ResourceLocation policy, BlockPos venueAnchor,
                   Map<UUID, Participant> participants, long createdAt, long arrivalDeadline) {
        this.id = Objects.requireNonNull(id);
        this.dimension = Objects.requireNonNull(dimension);
        this.venueDefinition = Objects.requireNonNull(venueDefinition);
        this.buildingId = buildingId;
        this.activity = Objects.requireNonNull(activity);
        this.policy = Objects.requireNonNull(policy);
        this.venueAnchor = new BlockPos(
                venueAnchor.getX(), venueAnchor.getY(), venueAnchor.getZ());
        this.participants = new LinkedHashMap<>(participants);
        this.createdAt = createdAt;
        this.deadline = arrivalDeadline;
        this.phase = Phase.TRAVELING;
    }

    public UUID id() { return id; }
    public ResourceLocation dimension() { return dimension; }
    public ResourceLocation venueDefinition() { return venueDefinition; }
    public int buildingId() { return buildingId; }
    public ResourceLocation activity() { return activity; }
    public ResourceLocation policy() { return policy; }
    public BlockPos venueAnchor() { return venueAnchor; }
    public Map<UUID, Participant> participants() { return Map.copyOf(participants); }
    Map<UUID, Participant> mutableParticipants() { return participants; }
    public Phase phase() { return phase; }
    public long createdAt() { return createdAt; }
    public long deadline() { return deadline; }
    public long activeAt() { return activeAt; }
    public long lastTick() { return lastTick; }
    public String exitReason() { return exitReason; }

    void markTick(long tick) { lastTick = tick; }
    void activate(long now, long activeDeadline) { phase = Phase.ACTIVE; activeAt = now; deadline = activeDeadline; }
    void leave(String reason) { phase = Phase.LEAVING; exitReason = reason == null ? "" : reason; }
    void finish() { phase = Phase.COMPLETE; }
    void interrupt(String reason) { phase = Phase.INTERRUPTED; exitReason = reason == null ? "" : reason; }
}
