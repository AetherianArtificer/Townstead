package com.aetherianartificer.townstead.pheno.action.block;

/**
 * A behavior run at a block (Apoli's {@code block_action}). Always server-side.
 */
@FunctionalInterface
public interface BlockAction {

    /**
     * Whether this action can do useful work in the supplied context without changing the world.
     * Most Pheno actions are always applicable. Stateful integrations can override this so a
     * generic Job does not claim a block whose procedure is waiting or needs a different item.
     */
    default boolean canRun(BlockActionContext ctx) {
        return true;
    }

    void run(BlockActionContext ctx);
}
