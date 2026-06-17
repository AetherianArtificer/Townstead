package com.aetherianartificer.townstead.pheno.selector;

import com.google.gson.JsonObject;

/**
 * Pluggable selector-source contract (the object form of {@code on}), modeled on
 * {@code ConditionType}. A role string is handled directly by {@link Selectors}; objects dispatch
 * by {@code "type"} to a registered type. Register once at startup via {@link SelectorTypes#register}.
 */
public interface SelectorType {

    String key();

    Selector parse(JsonObject json);
}
