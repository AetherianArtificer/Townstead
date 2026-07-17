package com.aetherianartificer.townstead.chronicle.model;

import java.util.Objects;

/**
 * One participant in a chronicle event, bound to a template-defined role.
 * {@code witness} is a built-in role gathered automatically at emission.
 */
public record Participation(String role, ChronicleRef ref) {

    public static final String ROLE_WITNESS = "witness";

    public Participation {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(ref, "ref");
    }

    public boolean isWitness() {
        return ROLE_WITNESS.equals(role);
    }
}
