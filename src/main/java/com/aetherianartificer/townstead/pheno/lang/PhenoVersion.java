package com.aetherianartificer.townstead.pheno.lang;

import com.google.gson.JsonObject;
/**
 * The Pheno authoring-language version used inside a Townstead resource. The owning
 * document schema selects the language version; for example,
 * {@code "schema": "townstead:gene/v2"} uses Pheno v2 in a gene document.
 * Absent means {@link #V1} for compatibility.
 *
 * <p>The former integer {@code pheno_version} field remains a compatibility fallback.
 * A recognized document {@code schema} is authoritative when both are present. The version is never inferred
 * from which fields happen to appear, so ambiguous legacy data is never silently
 * reinterpreted.
 */
public enum PhenoVersion {
    V1(1),
    V2(2);

    public static final PhenoVersion LATEST = V2;

    private final int number;

    PhenoVersion(int number) {
        this.number = number;
    }

    public int number() {
        return number;
    }

    public boolean atLeast(PhenoVersion other) {
        return this.number >= other.number;
    }

    /** Read the declared version from a resource root, defaulting to {@link #V1} when absent. */
    public static PhenoVersion of(JsonObject root) {
        if (root == null) return V1;
        if (root.has("schema")) {
            String schema = root.get("schema").getAsString();
            if ("townstead:gene/v2".equals(schema)) return V2;
            throw new IllegalArgumentException("Unknown gene schema '" + schema + "'");
        }
        if (!root.has("pheno_version")) return V1;
        int n = root.get("pheno_version").getAsInt();
        return n >= 2 ? V2 : V1;
    }
}
