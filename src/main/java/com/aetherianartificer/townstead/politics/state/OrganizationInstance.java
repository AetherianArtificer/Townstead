package com.aetherianartificer.townstead.politics.state;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Persistent identity of one organization in a world. Definitions supply rules; this is history. */
public record OrganizationInstance(ResourceLocation id,
                                   ResourceLocation kind,
                                   ResourceLocation membershipPolicy,
                                   String name,
                                   String shortName,
                                   int color,
                                   @Nullable ResourceLocation emblem,
                                   long createdAt,
                                   ResourceLocation provenance,
                                   PoliticalStatus.Organization status,
                                   @Nullable SettlementRef home) {
    public OrganizationInstance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(membershipPolicy, "membershipPolicy");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(shortName, "shortName");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(status, "status");
        name = name.trim();
        shortName = shortName.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Organization name cannot be empty");
    }

    public PoliticalActorRef actor() {
        return new PoliticalActorRef(PoliticalActorRef.Kind.ORGANIZATION, id);
    }
}
