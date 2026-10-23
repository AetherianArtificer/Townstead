package com.aetherianartificer.townstead.storage;

/**
 * Why a villager is consulting storage. Container roles are useful only when reads and writes
 * state their intent; a finished-goods chest must not become an ingredient bin merely because it
 * contains something edible.
 */
public enum StorageUse {
    INGREDIENT,
    TOOL,
    OUTPUT,
    TOOL_RETURN,
    PERSONAL
}
