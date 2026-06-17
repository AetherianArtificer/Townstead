package com.aetherianartificer.townstead.pheno.condition.bientity.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionType;
import com.google.gson.JsonObject;

/**
 * Applies an entity {@code condition} to the actor, the target, or both (Apoli's
 * bi-entity {@code actor_condition} / {@code target_condition} / {@code both} /
 * {@code either} metas), selected by {@link Scope}.
 *
 * <p>JSON: {@code { "type":"pheno:target_condition",
 * "condition":{ "type":"pheno:on_fire" } }}</p>
 */
public final class EntityScopedBiConditionType implements BiEntityConditionType {

    public enum Scope { ACTOR, TARGET, BOTH, EITHER }

    private final String key;
    private final Scope scope;

    public EntityScopedBiConditionType(String key, Scope scope) {
        this.key = key;
        this.scope = scope;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public BiEntityCondition parse(JsonObject json) {
        Condition inner = Conditions.parse(json.get("condition"));
        if (inner == null) return null;
        return (actor, target) -> switch (scope) {
            case ACTOR -> inner.test(new ConditionContext(actor));
            case TARGET -> inner.test(new ConditionContext(target));
            case BOTH -> inner.test(new ConditionContext(actor)) && inner.test(new ConditionContext(target));
            case EITHER -> inner.test(new ConditionContext(actor)) || inner.test(new ConditionContext(target));
        };
    }
}
