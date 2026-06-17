package com.aetherianartificer.townstead.pheno.capability;

import java.util.Collections;
import java.util.Map;

/**
 * The resolved capabilities of an entity at one instant: a read-only map of key to
 * {@link Resolved}. This is what Townstead systems query instead of inspecting genes or
 * skills. Missing keys fall back to the caller-supplied default (or the key's off/identity).
 */
public record CapabilityView(Map<CapabilityKey, Resolved> map) {

    public static final CapabilityView EMPTY = new CapabilityView(Collections.emptyMap());

    public boolean flag(CapabilityKey key) {
        Resolved r = map.get(key);
        return r != null && r.flag();
    }

    public double numeric(CapabilityKey key, double fallback) {
        Resolved r = map.get(key);
        return r != null ? r.number() : fallback;
    }

    public Resolved resolved(CapabilityKey key) {
        return map.get(key);
    }
}
