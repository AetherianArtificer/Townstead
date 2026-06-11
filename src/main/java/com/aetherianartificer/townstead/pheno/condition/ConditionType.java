package com.aetherianartificer.townstead.pheno.condition;

import com.google.gson.JsonObject;

/**
 * Pluggable condition-type contract, modeled on {@code GeneType}. A condition
 * JSON names a type via its {@code "type"} field; that type parses the config
 * into a {@link Condition}. Register implementations once at startup via
 * {@link ConditionTypes#register}.
 */
public interface ConditionType {

    /** Wire key matched against a condition JSON's {@code "type"} (e.g. {@code townstead_origins:in_rain}). */
    String key();

    /** Parse this type's config into a predicate; return {@code null} if invalid. */
    Condition parse(JsonObject json);
}
