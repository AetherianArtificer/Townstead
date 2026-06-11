package com.aetherianartificer.townstead.pheno.lang.compile;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One compiler finding against a single resource, located by an exact JSON path
 * ({@code $.action.do.duration}). The optional {@code suggestion} is human-facing fix
 * guidance; {@code migration} is a machine-applicable canonical replacement for the value
 * at {@code jsonPath} (used by {@code /pheno expand} and migration tooling) or {@code null}
 * when no automatic rewrite is known.
 */
public record Diagnostic(
        ResourceLocation resource,
        String jsonPath,
        Severity severity,
        String message,
        @Nullable String suggestion,
        @Nullable String migration) {

    public static Diagnostic error(ResourceLocation resource, String jsonPath, String message) {
        return new Diagnostic(resource, jsonPath, Severity.ERROR, message, null, null);
    }

    public static Diagnostic error(ResourceLocation resource, String jsonPath, String message, String suggestion) {
        return new Diagnostic(resource, jsonPath, Severity.ERROR, message, suggestion, null);
    }

    public static Diagnostic warning(ResourceLocation resource, String jsonPath, String message, String suggestion) {
        return new Diagnostic(resource, jsonPath, Severity.WARNING, message, suggestion, null);
    }

    public static Diagnostic info(ResourceLocation resource, String jsonPath, String message, String suggestion) {
        return new Diagnostic(resource, jsonPath, Severity.INFO, message, suggestion, null);
    }

    /** A single-line rendering: {@code [ERROR] ns:id $.path  message (suggestion)}. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(severity).append("] ").append(resource).append(' ').append(jsonPath);
        sb.append("  ").append(message);
        if (suggestion != null) sb.append("  -> ").append(suggestion);
        return sb.toString();
    }
}
