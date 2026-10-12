package com.aetherianartificer.townstead.root;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A mix's shape: two to four distinct roots whose quarter shares make a whole. */
class RootMixTest {

    private static RootMix mix(Object... idsAndQuarters) {
        List<RootMix.Member> members = new java.util.ArrayList<>();
        for (int i = 0; i < idsAndQuarters.length; i += 2) {
            members.add(new RootMix.Member((String) idsAndQuarters[i], (Integer) idsAndQuarters[i + 1]));
        }
        return new RootMix(members);
    }

    @Test
    void wholeSharesOfDistinctRootsAreWellFormed() {
        assertTrue(mix("a:fire", 2, "a:ice", 2).wellFormed());
        assertTrue(mix("a:fire", 2, "a:ice", 1, "a:volt", 1).wellFormed());
        assertTrue(mix("a:w", 1, "a:x", 1, "a:y", 1, "a:z", 1).wellFormed());
    }

    @Test
    void refusesOneRootSharesOffTheWholeAndRepeats() {
        assertFalse(mix("a:fire", 4).wellFormed());
        assertFalse(mix("a:fire", 2, "a:ice", 1).wellFormed());
        assertFalse(mix("a:fire", 3, "a:ice", 0, "a:volt", 1).wellFormed());
        assertFalse(mix("a:fire", 2, "a:fire", 2).wellFormed());
    }

    @Test
    void largestShareLeadsAndTheFirstWinsATie() {
        assertEquals("a:ice", mix("a:fire", 1, "a:ice", 3).largest());
        assertEquals("a:fire", mix("a:fire", 2, "a:ice", 2).largest());
    }

    @Test
    void sharesReadAsQuarters() {
        assertEquals("1/4", RootMix.shareLabel(1));
        assertEquals("1/2", RootMix.shareLabel(2));
        assertEquals("3/4", RootMix.shareLabel(3));
    }
}
