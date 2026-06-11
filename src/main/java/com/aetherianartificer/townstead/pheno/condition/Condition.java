package com.aetherianartificer.townstead.pheno.condition;

/**
 * A parsed predicate over an entity's live state, used to gate a conditioned gene
 * (e.g. an attribute that only applies at night). Mirrors Apoli's entity
 * conditions; the Townstead subset is registered as {@link ConditionType}s.
 */
@FunctionalInterface
public interface Condition {

    boolean test(ConditionContext ctx);

    default Condition negate() {
        return ctx -> !test(ctx);
    }
}
