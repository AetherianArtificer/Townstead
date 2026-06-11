package com.aetherianartificer.townstead.pheno.lang.compile;

/**
 * Severity of a compile {@link Diagnostic}. {@link #ERROR} means the resource will not
 * behave as written (an unusable type, a missing required field); {@link #WARNING} means it
 * compiles but is suspect (a deprecated field, an expensive selector); {@link #INFO} and
 * {@link #HINT} are advisory (a migration suggestion, a redundant clause).
 */
public enum Severity {
    ERROR,
    WARNING,
    INFO,
    HINT;
}
