package com.aetherianartificer.townstead.pheno.lang.validate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChildSlotsTest {

    @Test
    void ifBientityConditionIsABientityCondition() {
        assertEquals(NodeDomain.BIENTITY_CONDITION,
                ChildSlots.childDomain(NodeDomain.ACTION, "condition", "pheno:if_bientity"));
    }

    @Test
    void otherActionConditionsStayEntityConditions() {
        assertEquals(NodeDomain.CONDITION, ChildSlots.childDomain(NodeDomain.ACTION, "condition", "pheno:if_else"));
    }

    @Test
    void directionalWrappersHoldBientityConditions() {
        assertEquals(NodeDomain.BIENTITY_CONDITION,
                ChildSlots.childDomain(NodeDomain.BIENTITY_CONDITION, "condition", "pheno:invert"));
        assertEquals(NodeDomain.BIENTITY_CONDITION,
                ChildSlots.childDomain(NodeDomain.BIENTITY_CONDITION, "condition", "pheno:undirected"));
    }

    @Test
    void actorConditionStaysAnEntityCondition() {
        assertEquals(NodeDomain.CONDITION,
                ChildSlots.childDomain(NodeDomain.BIENTITY_CONDITION, "condition", "pheno:actor_condition"));
    }
}
