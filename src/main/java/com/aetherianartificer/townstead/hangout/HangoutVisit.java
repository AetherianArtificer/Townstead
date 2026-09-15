package com.aetherianartificer.townstead.hangout;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** One resident's independently owned attendance at a venue. */
public final class HangoutVisit {
    public enum Phase { TRAVELING, PRESENT, COMPLETE, INTERRUPTED }

    public record Visitor(UUID entity, BlockPos spot, BlockPos approach,
                          ResourceLocation posture, ResourceLocation adapter,
                          Vec3 embodimentPosition, @Nullable HangoutSpot.RestBonus rest,
                          @Nullable HangoutEmbodiment.Handle handle) {
        Visitor withHandle(HangoutEmbodiment.Handle next) {
            return new Visitor(entity, spot, approach, posture, adapter, embodimentPosition, rest, next);
        }
    }

    private final UUID id;
    private final ResourceLocation dimension;
    private final ResourceLocation venueDefinition;
    private final int buildingId;
    private final ResourceLocation policy;
    private final BlockPos venueAnchor;
    private Visitor visitor;
    private Phase phase = Phase.TRAVELING;
    private final long createdAt;
    private long deadline;
    private long presentAt;
    private long nextBeatAt;
    private long nextDrinkAt;
    private long lastTick = Long.MIN_VALUE;
    private String exitReason = "";

    HangoutVisit(UUID id, ResourceLocation dimension, ResourceLocation venueDefinition,
                 int buildingId, ResourceLocation policy, BlockPos venueAnchor, Visitor visitor,
                 long createdAt, long arrivalDeadline) {
        this.id = Objects.requireNonNull(id);
        this.dimension = Objects.requireNonNull(dimension);
        this.venueDefinition = Objects.requireNonNull(venueDefinition);
        this.buildingId = buildingId;
        this.policy = Objects.requireNonNull(policy);
        this.venueAnchor = venueAnchor.immutable();
        this.visitor = Objects.requireNonNull(visitor);
        this.createdAt = createdAt;
        this.deadline = arrivalDeadline;
    }

    public UUID id() { return id; }
    public ResourceLocation dimension() { return dimension; }
    public ResourceLocation venueDefinition() { return venueDefinition; }
    public int buildingId() { return buildingId; }
    public ResourceLocation policy() { return policy; }
    public BlockPos venueAnchor() { return venueAnchor; }
    public Visitor visitor() { return visitor; }
    public Phase phase() { return phase; }
    public long createdAt() { return createdAt; }
    public long deadline() { return deadline; }
    public long presentAt() { return presentAt; }
    public long nextBeatAt() { return nextBeatAt; }
    long nextDrinkAt() { return nextDrinkAt; }
    void deferDrink(long until) { nextDrinkAt = until; }
    public long lastTick() { return lastTick; }
    public String exitReason() { return exitReason; }

    void markTick(long tick) { lastTick = tick; }
    void arrive(long now, long departure) {
        phase = Phase.PRESENT;
        presentAt = now;
        deadline = departure;
        nextBeatAt = now;
        nextDrinkAt = now + 60 + Math.floorMod(id.hashCode(), 141);
    }
    void setHandle(HangoutEmbodiment.Handle handle) { visitor = visitor.withHandle(handle); }
    void deferBeat(long until) { nextBeatAt = until; }
    void complete(String reason) { phase = Phase.COMPLETE; exitReason = reason == null ? "" : reason; }
    void interrupt(String reason) { phase = Phase.INTERRUPTED; exitReason = reason == null ? "" : reason; }
}
