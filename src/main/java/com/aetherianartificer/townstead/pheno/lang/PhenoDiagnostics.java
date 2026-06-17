package com.aetherianartificer.townstead.pheno.lang;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.pheno.lang.compile.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the diagnostics from the most recent compile of each data source (genes, professions,
 * skills, ...). Each source replaces its own slice on reload, and {@code /pheno validate}
 * reports the aggregate.
 */
public final class PhenoDiagnostics {

    private static final Map<String, List<Diagnostic>> BY_SOURCE = new ConcurrentHashMap<>();

    private PhenoDiagnostics() {}

    /** Replace one source's diagnostics (e.g. "gene", "profession"). */
    public static void replace(String source, List<Diagnostic> diagnostics) {
        BY_SOURCE.put(source, List.copyOf(diagnostics));
    }

    public static List<Diagnostic> all() {
        List<Diagnostic> out = new ArrayList<>();
        for (Map.Entry<String, List<Diagnostic>> e : new TreeMap<>(BY_SOURCE).entrySet()) {
            out.addAll(e.getValue());
        }
        return out;
    }

    public static int count(Severity severity) {
        int n = 0;
        for (List<Diagnostic> list : BY_SOURCE.values()) {
            for (Diagnostic d : list) {
                if (d.severity() == severity) n++;
            }
        }
        return n;
    }
}
