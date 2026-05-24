package com.aetherianartificer.townstead.origin.gene;

import java.util.Locale;

/**
 * Whether a gene is expressed over its allele-group rivals at inheritance.
 * Resolution (dominant beats recessive, ties by weight then random) is a
 * deferred runtime phase; this iteration only carries/displays it.
 */
public enum Dominance {
    DOMINANT,
    RECESSIVE;

    public static Dominance fromString(String s) {
        if (s != null && "recessive".equals(s.toLowerCase(Locale.ROOT))) return RECESSIVE;
        return DOMINANT;
    }
}
