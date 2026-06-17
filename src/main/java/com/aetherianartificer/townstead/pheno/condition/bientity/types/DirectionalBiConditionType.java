package com.aetherianartificer.townstead.pheno.condition.bientity.types;

import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionType;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditions;
import com.google.gson.JsonObject;

/**
 * Wraps another bi-entity {@code condition}, either swapping the pair ({@code invert})
 * or testing it both ways ({@code undirected}; true if either direction holds), selected
 * by {@link Mode}.
 *
 * <p>JSON: {@code { "type":"pheno:undirected",
 * "condition":{ "type":"pheno:riding" } }}</p>
 */
public final class DirectionalBiConditionType implements BiEntityConditionType {

    public enum Mode { INVERT, UNDIRECTED }

    private final String key;
    private final Mode mode;

    public DirectionalBiConditionType(String key, Mode mode) {
        this.key = key;
        this.mode = mode;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public BiEntityCondition parse(JsonObject json) {
        BiEntityCondition inner = BiEntityConditions.parse(json.get("condition"));
        if (inner == null) return null;
        return (actor, target) -> switch (mode) {
            case INVERT -> inner.test(target, actor);
            case UNDIRECTED -> inner.test(actor, target) || inner.test(target, actor);
        };
    }
}
