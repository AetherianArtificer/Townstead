package com.aetherianartificer.townstead.pheno.action.block;

import com.google.gson.JsonObject;

/**
 * Pluggable block-action-type contract, modeled on {@code ActionType}. A block-action
 * JSON names a type via {@code "type"}; that type parses the config into a
 * {@link BlockAction}. Register once at startup via {@link BlockActionTypes#register}.
 */
public interface BlockActionType {

    /** Wire key matched against a block-action JSON's {@code "type"}. */
    String key();

    /** Parse this type's config into a runnable block action; {@code null} if invalid. */
    BlockAction parse(JsonObject json);
}
