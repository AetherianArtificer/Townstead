package com.aetherianartificer.townstead.politics.state;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Set;

/** Organization membership: an affiliation plus its organization-scoped role assignments. */
public record MembershipInstance(AffiliationInstance affiliation,
                                 ResourceLocation membershipPolicy,
                                 ResourceLocation admissionProcedure,
                                 ResourceLocation departureProcedure,
                                 Set<ResourceLocation> roles) {
    public MembershipInstance {
        Objects.requireNonNull(affiliation, "affiliation");
        Objects.requireNonNull(membershipPolicy, "membershipPolicy");
        Objects.requireNonNull(admissionProcedure, "admissionProcedure");
        Objects.requireNonNull(departureProcedure, "departureProcedure");
        roles = Set.copyOf(roles);
        if (affiliation.actor().kind() != PoliticalActorRef.Kind.ORGANIZATION) {
            throw new IllegalArgumentException("Memberships must point to an organization");
        }
    }

    public ResourceLocation id() {
        return affiliation.id();
    }
}
