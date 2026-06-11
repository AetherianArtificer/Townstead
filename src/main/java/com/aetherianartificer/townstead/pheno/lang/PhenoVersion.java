package com.aetherianartificer.townstead.pheno.lang;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * The Pheno authoring-language version a resource is written in, declared explicitly with a
 * top-level {@code "pheno_version"} field. Absent means {@link #V1} (the original
 * Apoli-shaped vocabulary), which stays fully supported. {@link #V2} unlocks the canonical
 * vocabulary (on/when/do/with, the {@code gene} envelope, consolidated conditions).
 *
 * <p>The version is read from the explicit field only. It is never inferred from which
 * fields happen to appear, so ambiguous legacy data is never silently reinterpreted.
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
        if (root == null || !root.has("pheno_version")) return V1;
        int n = GsonHelper.getAsInt(root, "pheno_version", 1);
        return n >= 2 ? V2 : V1;
    }
}
