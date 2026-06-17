package com.aetherianartificer.townstead.pheno.capability;

/**
 * Where a capability contribution came from, for provenance and {@code /pheno explain}. The
 * label is the short word shown in an explain trace.
 */
public enum SourceKind {
    GENE("gene"),
    PROFESSION("profession"),
    SKILL("skill"),
    EQUIPMENT("equipment"),
    MOOD("mood"),
    BUILDING("building"),
    OTHER("other");

    private final String label;

    SourceKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
