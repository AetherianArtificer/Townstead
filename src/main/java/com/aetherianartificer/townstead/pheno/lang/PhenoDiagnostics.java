package com.aetherianartificer.townstead.pheno.lang;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.pheno.lang.compile.Severity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds the diagnostics from the most recent datapack compile so {@code /pheno validate} can
 * report them without recompiling. Replaced wholesale on each resource reload.
 */
public final class PhenoDiagnostics {

    private static volatile List<Diagnostic> last = List.of();

    private PhenoDiagnostics() {}

    public static void replace(List<Diagnostic> diagnostics) {
        last = new CopyOnWriteArrayList<>(diagnostics);
    }

    public static List<Diagnostic> all() {
        return last;
    }

    public static int count(Severity severity) {
        int n = 0;
        for (Diagnostic d : last) {
            if (d.severity() == severity) n++;
        }
        return n;
    }
}
