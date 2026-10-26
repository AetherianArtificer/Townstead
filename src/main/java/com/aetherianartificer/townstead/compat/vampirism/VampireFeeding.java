package com.aetherianartificer.townstead.compat.vampirism;

/**
 * Whom a vampire villager may drink from besides animals. Each tier includes the one below it.
 * Another vampire is never prey.
 */
public enum VampireFeeding {
    /** Animals only. */
    OFF,
    /** Also a spouse, who offers. */
    WILLING,
    /** Also any villager or player who is not a vampire. */
    ANYONE
}
