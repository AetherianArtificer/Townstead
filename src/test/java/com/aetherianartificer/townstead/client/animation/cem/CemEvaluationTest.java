package com.aetherianartificer.townstead.client.animation.cem;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class CemEvaluationTest {
    @Test void conditionalSkipsUnselectedNbtAndLaterConditions() {
        // A null context makes any accidental entity/variable read fail immediately.
        assertEquals(7, CemExpressionParser.parse("if(true, 7, nbt(Inventory, exists:true))").evaluate(null));
        assertEquals(9, CemExpressionParser.parse("if(false, nbt(Inventory, exists:true), true, 9, missing, 2, 3)").evaluate(null));
        assertEquals(3, CemExpressionParser.parse("if(false, 1, false, 2, 3)").evaluate(null));
        assertEquals(0, CemExpressionParser.parse("if(false, 1)").evaluate(null));
    }

    @Test void nbtRegexUsesEtfWholeValueSemantics() {
        var exact = CemAnimationProgram.regexMatcher("minecraft:filled_map", false);
        assertTrue(exact.test("minecraft:filled_map"));
        assertFalse(exact.test("prefix minecraft:filled_map suffix"));
        assertTrue(CemAnimationProgram.regexMatcher(".*FILLED_MAP.*", true).test("{id:minecraft:filled_map}"));
        assertFalse(CemAnimationProgram.regexMatcher("[", false).test("anything"));
    }

    @Test void missingOffhandItemDoesNotRetryInventoryAtEveryCharacter() {
        var matcher = CemAnimationProgram.regexMatcher(".*Slot:-106b.*filled_map.*", true);
        String inventory = "[{id:minecraft:stone,components:{description:" + "x".repeat(100_000) + "}}]";
        assertTimeout(Duration.ofSeconds(2), () -> assertFalse(matcher.test(inventory)));
        assertTrue(matcher.test("[{Slot:-106b,id:minecraft:filled_map}]"));
    }
}
