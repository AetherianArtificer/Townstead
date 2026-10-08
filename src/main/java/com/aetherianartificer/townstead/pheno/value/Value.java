package com.aetherianartificer.townstead.pheno.value;

import com.aetherianartificer.townstead.pheno.selector.SelectorContext;

/**
 * A number that may be a literal or computed from a selection (the minimal expression hook). A
 * numeric field like {@code amount} parses to a {@link Value}, so {@code "amount": 4} and
 * {@code "amount": { "type": "pheno:count", "on": {...} }} are both valid. Resolved against the
 * current focus, so a count inside an {@code on} block anchors to that target.
 */
@FunctionalInterface
public interface Value {

    double get(SelectorContext ctx);

    /**
     * True when this number can be computed about a
     * {@link com.aetherianartificer.townstead.pheno.condition.PhenoSubject}: someone
     * described by facts, with no world to measure. Most values read live state and
     * cannot, so the default is false and callers must check, exactly as they do for
     * {@link com.aetherianartificer.townstead.pheno.condition.Condition}.
     */
    default boolean supportsSubject() {
        return false;
    }

    /** Wraps a number that can be computed about a subject as well as an entity. */
    static Value subjectAware(Value value) {
        return new Value() {
            @Override
            public double get(SelectorContext ctx) {
                return value.get(ctx);
            }

            @Override
            public boolean supportsSubject() {
                return true;
            }
        };
    }
}
