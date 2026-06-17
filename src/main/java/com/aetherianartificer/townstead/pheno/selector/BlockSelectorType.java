package com.aetherianartificer.townstead.pheno.selector;

import com.google.gson.JsonObject;

/**
 * Pluggable block-selector source (the {@code type} object form of a block {@code on}), the block
 * analogue of {@link SelectorType}. Lets a genetics-side source (a block collection) register
 * without {@code pheno} depending on it. Register via {@link BlockSelectorTypes#register}.
 */
public interface BlockSelectorType {

    String key();

    BlockSelector parse(JsonObject json);
}
