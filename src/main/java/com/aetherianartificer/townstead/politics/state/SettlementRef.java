package com.aetherianartificer.townstead.politics.state;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Stable MCA settlement identity: village ids are allocated independently in each dimension. */
public record SettlementRef(ResourceLocation dimension, int villageId) {
    public SettlementRef {
        Objects.requireNonNull(dimension, "dimension");
    }
}
