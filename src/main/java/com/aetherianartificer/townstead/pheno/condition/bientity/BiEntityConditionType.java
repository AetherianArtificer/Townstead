package com.aetherianartificer.townstead.pheno.condition.bientity;

import com.google.gson.JsonObject;

/**
 * Pluggable bi-entity-condition contract, modeled on {@code ConditionType}. Register
 * once at startup via {@link BiEntityConditionTypes#register}.
 */
public interface BiEntityConditionType {

    /** Wire key matched against a bi-entity-condition JSON's {@code "type"}. */
    String key();

    /** Parse this type's config into a runnable condition; {@code null} if invalid. */
    BiEntityCondition parse(JsonObject json);
}
