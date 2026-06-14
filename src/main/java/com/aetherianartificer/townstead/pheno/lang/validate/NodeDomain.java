package com.aetherianartificer.townstead.pheno.lang.validate;

import com.aetherianartificer.townstead.origin.gene.GeneTypes;
import com.aetherianartificer.townstead.pheno.action.ActionTypes;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes;
import com.aetherianartificer.townstead.pheno.action.item.ItemActionTypes;
import com.aetherianartificer.townstead.pheno.condition.ConditionTypes;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionTypes;

/**
 * A node domain the validator can resolve a {@code "type"} key against. Each domain delegates
 * to the live registry for that kind of node, so unknown-type diagnostics stay in lockstep
 * with whatever is actually registered (no separate allow-list to drift).
 */
public enum NodeDomain {
    GENE("gene type") {
        @Override public boolean resolves(String key) { return GeneTypes.get(key).isPresent(); }
    },
    ACTION("action") {
        @Override public boolean resolves(String key) { return ActionTypes.get(key).isPresent(); }
    },
    CONDITION("condition") {
        @Override public boolean resolves(String key) { return ConditionTypes.get(key).isPresent(); }
    },
    BIENTITY_CONDITION("bi-entity condition") {
        @Override public boolean resolves(String key) { return BiEntityConditionTypes.get(key).isPresent(); }
    },
    BLOCK_ACTION("block action") {
        @Override public boolean resolves(String key) { return BlockActionTypes.get(key).isPresent(); }
    },
    ITEM_ACTION("item action") {
        @Override public boolean resolves(String key) { return ItemActionTypes.get(key).isPresent(); }
    },
    /** Plain data records (attachments, ...) with no behavior-node registry to resolve against. */
    DATA("data") {
        @Override public boolean resolves(String key) { return false; }
    };

    private final String label;

    NodeDomain(String label) {
        this.label = label;
    }

    /** Human-facing name for diagnostics, e.g. "unknown action type". */
    public String label() {
        return label;
    }

    public abstract boolean resolves(String key);
}
