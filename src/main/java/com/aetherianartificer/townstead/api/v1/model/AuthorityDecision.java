package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Explainable result of an actor-scoped capability query. */
public record AuthorityDecision(boolean allowed,
                                ResourceLocation reason,
                                Optional<ResourceLocation> grantingRole) {
}
