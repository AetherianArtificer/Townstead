package com.aetherianartificer.townstead.pheno;

import com.aetherianartificer.townstead.origin.gene.types.ReachHook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Edge-trigger logic for {@link ReachHook#crossed}: fire once on an upward crossing, never downward. */
class ReachHookTest {

    private static ReachHook at(int at) {
        return new ReachHook(at, 0, ctx -> {}, false);
    }

    private static ReachHook every(int every) {
        return new ReachHook(0, every, ctx -> {}, false);
    }

    @Test
    void atFiresOnlyWhenCrossingTheThresholdUpward() {
        ReachHook hook = at(100);
        assertTrue(hook.crossed(99, 100), "reaching exactly the threshold fires");
        assertTrue(hook.crossed(50, 150), "jumping past it fires");
        assertFalse(hook.crossed(100, 101), "already at/above does not re-fire");
        assertFalse(hook.crossed(0, 99), "staying below does not fire");
        assertFalse(hook.crossed(150, 90), "falling does not fire");
        assertFalse(hook.crossed(100, 100), "no change does not fire");
    }

    @Test
    void everyFiresOncePerMultipleCrossed() {
        ReachHook hook = every(50);
        assertTrue(hook.crossed(49, 50), "crossing the first multiple fires");
        assertTrue(hook.crossed(99, 100), "crossing the second fires");
        assertFalse(hook.crossed(50, 99), "staying within the same band does not fire");
        assertTrue(hook.crossed(40, 120), "a big jump that passes a multiple fires (once)");
        assertFalse(hook.crossed(120, 40), "falling does not fire");
    }
}
