package com.aetherianartificer.townstead.pheno.lang.schema;

/**
 * A named context role a node may read. Events expose a subset of these; a node's
 * {@code acceptedContext} declares which roles it requires, so the compiler can reject a node
 * used where its context is not available (full enforcement arrives with the event layer).
 */
public enum Role {
    SELF,
    ACTOR,
    TARGET,
    ATTACKER,
    VICTIM,
    OWNER,
    ITEM,
    BLOCK,
    POSITION,
    BUILDING,
    WORKPLACE,
    VILLAGE,
    AUDIENCE,
    DAMAGE;
}
