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
                    case "condition":
                    case "conditions":
                        return NodeDomain.CONDITION;
                    case "bientity_condition":
                        return NodeDomain.BIENTITY_CONDITION;
                    default:
                        return null;
                }
            case BLOCK_ACTION:
                return key.equals("block_action") ? NodeDomain.BLOCK_ACTION : null;
            case ITEM_ACTION:
                if (key.equals("item_action")) return NodeDomain.ITEM_ACTION;
                if (key.equals("action")) return NodeDomain.ACTION;
                return null;
            default:
                return null;
        }
    }
}
