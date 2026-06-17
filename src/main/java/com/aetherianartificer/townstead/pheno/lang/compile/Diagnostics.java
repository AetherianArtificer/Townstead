package com.aetherianartificer.townstead.pheno.lang.compile;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * A mutable sink that accumulates {@link Diagnostic}s during a compile pass. Holds the
 * resource currently being compiled so callers pass only a path + message. Query helpers
 * ({@link #hasErrors()}, {@link #count(Severity)}) drive load-time logging and the
 * {@code /pheno validate} report.
 */
public final class Diagnostics {

    private final List<Diagnostic> entries = new ArrayList<>();
    private ResourceLocation resource;

    public Diagnostics() {}

    public Diagnostics(ResourceLocation resource) {
        this.resource = resource;
    }

    /** Re-point the sink at a new resource (when one collector spans a whole pack). */
    public void forResource(ResourceLocation resource) {
        this.resource = resource;
    }

    public void add(JsonPath path, Severity severity, String message, String suggestion) {
        entries.add(new Diagnostic(resource, path.toString(), severity, message, suggestion, null));
    }

    public void error(JsonPath path, String message, String suggestion) {
        entries.add(new Diagnostic(resource, path.toString(), Severity.ERROR, message, suggestion, null));
    }

    public void warning(JsonPath path, String message, String suggestion) {
        entries.add(new Diagnostic(resource, path.toString(), Severity.WARNING, message, suggestion, null));
    }

    public void add(Diagnostic diagnostic) {
        entries.add(diagnostic);
    }

    public List<Diagnostic> all() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean hasErrors() {
        return count(Severity.ERROR) > 0;
    }

    public int count(Severity severity) {
        int n = 0;
        for (Diagnostic d : entries) {
            if (d.severity() == severity) n++;
        }
        return n;
    }
}
