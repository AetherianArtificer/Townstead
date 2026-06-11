package com.aetherianartificer.townstead.pheno.action.item;

import com.google.gson.JsonObject;

/**
 * Pluggable item-action-type contract, modeled on {@code ActionType}. An item-action
 * JSON names a type via {@code "type"}; that type parses the config into an
 * {@link ItemAction}. Register once at startup via {@link ItemActionTypes#register}.
 */
public interface ItemActionType {

    /** Wire key matched against an item-action JSON's {@code "type"}. */
    String key();

    /** Parse this type's config into a runnable item action; {@code null} if invalid. */
    ItemAction parse(JsonObject json);
}
