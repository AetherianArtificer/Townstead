package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Organization membership and only the roles assigned inside that organization. */
public record MembershipSnapshot(AffiliationSnapshot affiliation,
                                 ResourceLocation membershipPolicy,
                                 ResourceLocation admissionProcedure,
                                 ResourceLocation departureProcedure,
                                 Set<ResourceLocation> roles) {
    public MembershipSnapshot {
        roles = Set.copyOf(roles);
    }
}
