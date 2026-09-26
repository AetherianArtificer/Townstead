package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Immutable public identity of one persistent organization. */
public record OrganizationSnapshot(ResourceLocation id,
                                   ResourceLocation kind,
                                   ResourceLocation membershipPolicy,
                                   String name,
                                   String shortName,
                                   int color,
                                   Optional<ResourceLocation> emblem,
                                   long createdAt,
                                   ResourceLocation provenance,
                                   String status,
                                   Optional<VillageId> home,
                                   int activeMembershipCount) {
}
