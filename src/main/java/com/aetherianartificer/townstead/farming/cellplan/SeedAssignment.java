package com.aetherianartificer.townstead.farming.cellplan;

public final class SeedAssignment {
    public static final String AUTO = "auto";
    public static final String NONE = "none";
    public static final String PROTECTED = "protected";

    private SeedAssignment() {}

    public static boolean isExplicitSeed(String value) {
        return value != null && !value.isBlank()
                && !AUTO.equals(value) && !NONE.equals(value) && !PROTECTED.equals(value);
    }
}
