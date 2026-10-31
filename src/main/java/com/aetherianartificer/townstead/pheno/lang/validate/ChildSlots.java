package com.aetherianartificer.townstead.pheno.lang.validate;

import org.jetbrains.annotations.Nullable;

/**
 * The canonical typed child slots of each node domain: which child keys hold which kind of
 * nested node. Shared by the validator (to descend and resolve nested types) and the
 * normalizer (to descend and rewrite nested sugar). Only unambiguous slots are mapped, so
 * neither tool ever guesses wrong about a context-dependent slot.
 */
public final class ChildSlots {

    private ChildSlots() {}

    @Nullable
    public static NodeDomain childDomain(NodeDomain parent, String key) {
        return childDomain(parent, key, "");
    }

    /**
     * As above, for a parent of a known type. A few slots mean different things under different
     * types: an item action's {@code action} is an entity action under {@code holder_action} but an
     * item action under {@code chance}; a bi-entity {@code condition} is an entity condition under
     * {@code actor_condition} but a bi-entity condition under {@code not}; an action's
     * {@code condition} is a bi-entity condition only under {@code if_bientity}.
     */
    @Nullable
    public static NodeDomain childDomain(NodeDomain parent, String key, String parentType) {
        if (parent == NodeDomain.ITEM_ACTION && !parentType.isEmpty()
                && !parentType.equals("pheno:holder_action")) {
            if (key.equals("action") || key.equals("actions") || key.equals("else_action")) return NodeDomain.ITEM_ACTION;
        }
        // if_bientity tests its condition against the (actor, target) pair.
        if (parent == NodeDomain.ACTION && parentType.equals("pheno:if_bientity") && key.equals("condition")) {
            return NodeDomain.BIENTITY_CONDITION;
        }
        if (parent == NodeDomain.BIENTITY_CONDITION) {
            boolean logic = parentType.equals("pheno:and") || parentType.equals("pheno:or")
                    || parentType.equals("pheno:not") || parentType.equals("pheno:invert")
                    || parentType.equals("pheno:undirected");
            if (key.equals("condition") || key.equals("conditions")) {
                return logic ? NodeDomain.BIENTITY_CONDITION : NodeDomain.CONDITION;
            }
            return null;
        }
        switch (parent) {
            case GENE:
            case ACTION:
                switch (key) {
                    case "action":
                    case "entity_action":
                    case "actions":
                    case "bientity_action":
                        return NodeDomain.ACTION;
                    case "condition":
                    case "conditions":
                    case "mob_condition":
                        return NodeDomain.CONDITION;
                    case "block_action":
                        return NodeDomain.BLOCK_ACTION;
                    case "item_action":
                        return NodeDomain.ITEM_ACTION;
                    case "bientity_condition":
                        return NodeDomain.BIENTITY_CONDITION;
                    default:
                        return null;
                }
            case CONDITION:
                switch (key) {
                    case "value":
                    case "compare_to":
                        return NodeDomain.VALUE;
                    case "condition":
                    case "conditions":
                        return NodeDomain.CONDITION;
                    case "bientity_condition":
                        return NodeDomain.BIENTITY_CONDITION;
                    default:
                        return null;
                }
            case VALUE:
                if (key.equals("entity_condition")) return NodeDomain.CONDITION;
                if (key.equals("values") || key.equals("then") || key.equals("else")) return NodeDomain.VALUE;
                return null;
            case BLOCK_ACTION:
                if (key.equals("block_action") || key.equals("then") || key.equals("else")) {
                    return NodeDomain.BLOCK_ACTION;
                }
                if (key.equals("action")) return NodeDomain.ITEM_ACTION;
                return null;
            case ITEM_ACTION:
                if (key.equals("item_action")) return NodeDomain.ITEM_ACTION;
                if (key.equals("action")) return NodeDomain.ACTION;
                return null;
            default:
                return null;
        }
    }
}
