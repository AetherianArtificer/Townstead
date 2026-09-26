package com.aetherianartificer.townstead.politics.state;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** A durable sourced relationship between a person and a political actor. */
public record AffiliationInstance(ResourceLocation id,
                                  UUID person,
                                  PoliticalActorRef actor,
                                  ResourceLocation kind,
                                  PoliticalStatus.Affiliation status,
                                  long startedAt,
                                  long endedAt,
                                  ResourceLocation provenance,
                                  PoliticalStatus.Visibility visibility) {
    public static final long NOT_ENDED = Long.MIN_VALUE;

    public AffiliationInstance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(visibility, "visibility");
        if ((status == PoliticalStatus.Affiliation.FORMER
                || status == PoliticalStatus.Affiliation.REJECTED) && endedAt == NOT_ENDED) {
            throw new IllegalArgumentException("Terminal affiliations require an end time");
        }
    }

    public boolean active() {
        return status == PoliticalStatus.Affiliation.ACTIVE;
    }
}
