package com.aetherianartificer.townstead.pheno.action;

import com.google.gson.JsonObject;

/**
 * Pluggable action-type contract, modeled on {@code ConditionType}. An action JSON
 * names a type via {@code "type"}; that type parses the config into an
 * {@link Action}. Register once at startup via {@link ActionTypes#register}.
 */
public interface ActionType {

    /** Wire key matched against an action JSON's {@code "type"} (e.g. {@code townstead_origins:heal}). */
    String key();

    /** Parse this type's config into a runnable action; {@code null} if invalid. */
    Action parse(JsonObject json);
}
