package com.aetherianartificer.townstead.client.animation.cem;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CemInputPlanTest {
    @Test void computesOnlyReferencedInputsAndRefreshesThemEachEvaluation() {
        var layout = new CemVariableStore.Layout();
        CemExpressionParser.parse("var.HEALTH + age", layout);
        var builder = new CemInputPlan.Builder<double[]>(layout);
        int[] calls = {0};
        builder.add("health", state -> { calls[0]++; return state[0]; });
        builder.add("age", state -> state[1]);
        builder.add("distance", state -> { fail("Unused input must not be evaluated"); return 0; });
        var plan = builder.build();
        var variables = new CemVariableStore(layout);
        plan.seed(new double[] {20, 1}, variables);
        assertEquals(20, variables.get("health"));
        assertEquals(1, variables.get("age"));
        assertFalse(variables.wasAssigned("health"));
        plan.seed(new double[] {12, 2}, variables);
        assertEquals(12, variables.get("health"));
        assertEquals(2, variables.get("age"));
        assertEquals(2, calls[0]);
    }

    @Test void randomWithoutSeedDeclaresItsImplicitFrameCounterInput() {
        var layout = new CemVariableStore.Layout();
        CemExpressionParser.parse("random()", layout);
        assertTrue(layout.references("frame_counter"));
        var seeded = new CemVariableStore.Layout();
        CemExpressionParser.parse("random(17)", seeded);
        assertFalse(seeded.references("frame_counter"));
    }

    @Test void readingAnAuthoredVariableDoesNotReplaceItsPersistentValue() {
        var layout = new CemVariableStore.Layout();
        layout.reference("var.smoothing");
        var builder = new CemInputPlan.Builder<Void>(layout);
        builder.add("health", ignored -> { fail("Unused health input"); return 0; });
        var variables = new CemVariableStore(layout);
        variables.set("var.smoothing", 0.4);
        variables.clearAssignments();
        builder.build().seed(null, variables);
        assertEquals(0.4, variables.get("var.smoothing"));
    }
}
