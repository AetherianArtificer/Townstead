package com.aetherianartificer.townstead.pheno.action;

/**
 * A parsed self-action run when an active ability fires (apply an effect, launch,
 * heal, ...). Mirrors Apoli's entity actions; the Townstead subset is registered as
 * {@link ActionType}s.
 */
@FunctionalInterface
public interface Action {
    void run(ActionContext ctx);
}
