package com.aetherianartificer.townstead.client.animation.cem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CemVariableStoreTest {
    @Test void compiledSlotsPreserveAliasesAndEntityIsolation() {
        var layout = new CemVariableStore.Layout();
        int slot = layout.reference("var.WalkBlend");
        assertEquals(slot, layout.slot("varb.walkblend"));
        assertEquals(slot, layout.slot("walkblend"));
        var first = new CemVariableStore(layout);
        var second = new CemVariableStore(layout);
        first.set(slot, 0.75);
        assertEquals(0.75, first.get("var.WalkBlend"));
        assertEquals(0, second.get(slot));
        assertTrue(first.wasAssigned("varb.walkblend"));
        first.clearAssignments();
        assertFalse(first.wasAssigned("walkblend"));
        assertEquals(0.75, first.get(slot), "Persistent variables survive frames");
    }

    @Test void seedingDoesNotAssignTransformsAndGrowthPreservesValues() {
        var layout = new CemVariableStore.Layout();
        var store = new CemVariableStore(layout);
        store.seed("head.rx", 0.5);
        assertFalse(store.wasAssigned("head.rx"));
        for (int i = 0; i < 2048; i++) store.set(layout.slot("var.value" + i), i);
        assertEquals(0.5, store.get("head.rx"));
        assertEquals(2047, store.get("value2047"));
        assertEquals(0, store.get(layout.slot("uninitialized")));
        store.set("head.rx", Double.NaN);
        assertEquals(0, store.get("head.rx"));
        store.set("head.rx", Double.POSITIVE_INFINITY);
        assertEquals(0, store.get("head.rx"));
    }

    @Test void onlyExpressionReadsRequestExpensiveWorldInputs() {
        var layout = new CemVariableStore.Layout();
        CemExpressionParser.parse("var.height_above_ground + fluid_depth_down", layout);
        var store = new CemVariableStore(layout);
        assertTrue(store.references("height_above_ground"));
        assertTrue(store.references("fluid_depth_down"));
        assertFalse(store.references("fluid_depth_up"));
        store.seed("fluid_depth_up", 1);
        assertFalse(store.references("fluid_depth_up"));
    }
}
