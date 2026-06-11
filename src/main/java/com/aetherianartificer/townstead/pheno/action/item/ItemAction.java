package com.aetherianartificer.townstead.pheno.action.item;

/**
 * A behavior run on an item stack (Apoli's {@code item_action}). Mutates the stack in
 * place. Always server-side.
 */
@FunctionalInterface
public interface ItemAction {

    void run(ItemActionContext ctx);
}
