package com.aetherianartificer.townstead.pheno.capability;

import net.minecraft.resources.ResourceLocation;

/**
 * Identity of one resolvable capability: its id, value kind, and the {@code identity} value a
 * NUMERIC fold starts from (0 for an additive rating, 1 for a multiplicative one). FLAG keys
 * always start from off, so their identity is unused.
 */
public record CapabilityKey(ResourceLocation id, ValueKind kind, double identity) {

    public static CapabilityKey flag(ResourceLocation id) {
        return new CapabilityKey(id, ValueKind.FLAG, 0d);
    }

    /** A numeric capability that folds additively from 0 (a bonus/offset). */
    public static CapabilityKey additive(ResourceLocation id) {
        return new CapabilityKey(id, ValueKind.NUMERIC, 0d);
    }

    /** A numeric capability that folds multiplicatively from 1 (a scalar/multiplier). */
    public static CapabilityKey scalar(ResourceLocation id) {
        return new CapabilityKey(id, ValueKind.NUMERIC, 1d);
    }
}
