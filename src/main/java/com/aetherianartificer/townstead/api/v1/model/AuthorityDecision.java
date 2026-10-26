package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Explainable result of a faction-scoped capability query; {@code grantingBond} is the bond kind, such as an office, that allowed it. */
public record AuthorityDecision(boolean allowed,
                                ResourceLocation reason,
                                Optional<ResourceLocation> grantingBond) {
}
