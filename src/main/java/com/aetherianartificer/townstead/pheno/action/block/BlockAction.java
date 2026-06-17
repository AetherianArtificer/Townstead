package com.aetherianartificer.townstead.pheno.action.block;

/**
 * A behavior run at a block (Apoli's {@code block_action}). Always server-side.
 */
@FunctionalInterface
public interface BlockAction {

    void run(BlockActionContext ctx);
}
