package com.aetherianartificer.townstead.pheno.condition;

/**
 * A parsed predicate over an entity's live state, used to gate a conditioned gene
 * (e.g. an attribute that only applies at night). Mirrors Apoli's entity
 * conditions; the Townstead subset is registered as {@link ConditionType}s.
 *
 * <p>{@link #supportsSubject()} says whether this predicate can also answer
 * about a {@link PhenoSubject}: someone described by facts rather than present
 * in the world, which is how a fabricated past gates its roles. Most conditions
 * read live world state and cannot, so the default is false and callers must
 * check before evaluating without an entity. The flag travels with the compiled
 * condition, so a composite is subject-capable only when all of its parts are.</p>
 */
@FunctionalInterface
public interface Condition {

    boolean test(ConditionContext ctx);

    default boolean supportsSubject() {
        return false;
    }

    default Condition negate() {
        boolean supportsSubject = supportsSubject();
        return new Condition() {
            @Override
            public boolean test(ConditionContext ctx) {
                return !Condition.this.test(ctx);
            }

            @Override
            public boolean supportsSubject() {
                return supportsSubject;
            }
        };
    }

    /** Wraps a predicate that can answer about a subject as well as an entity. */
    static Condition subjectAware(Condition condition) {
        return new Condition() {
            @Override
            public boolean test(ConditionContext ctx) {
                return condition.test(ctx);
            }

            @Override
            public boolean supportsSubject() {
                return true;
            }
        };
    }
}
