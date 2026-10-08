package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;

/** An explicit no-op for optional branches in block-action composition. */
public final class NothingBlockActionType implements BlockActionType {
    public static final String KEY = "pheno:nothing";

    @Override public String key() { return KEY; }

    @Override public BlockAction parse(JsonObject json) { return context -> {}; }
}
