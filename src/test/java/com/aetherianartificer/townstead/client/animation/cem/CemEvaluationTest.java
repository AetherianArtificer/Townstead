package com.aetherianartificer.townstead.client.animation.cem;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.IntTag;
import static org.junit.jupiter.api.Assertions.*;

class CemEvaluationTest {
    @Test void foldsLiteralArithmeticAndFunctionsWithoutChangingFloatingPointOrder() {
        var expression = CemExpressionParser.parse("sin(0) + pow(0.5, 2) + (3 * 4 / 2)");
        assertInstanceOf(CemExpression.Constant.class, expression);
        assertEquals(6.25, expression.evaluate(null));
        assertEquals(0, CemExpressionParser.parse("(1e16 + 1) - 1e16").evaluate(null));
        assertEquals(Double.doubleToRawLongBits(-0.0),
                Double.doubleToRawLongBits(CemExpressionParser.parse("-0.0").evaluate(null)));
        assertTrue(Double.isNaN(CemExpressionParser.parse("0 / 0").evaluate(null)));
        assertEquals(Double.POSITIVE_INFINITY, CemExpressionParser.parse("1 / 0").evaluate(null));
    }

    @Test void foldingNeverFreezesStateOrEvaluatesDynamicBranches() {
        assertFalse(CemExpressionParser.parse("random()") instanceof CemExpression.Constant);
        assertFalse(CemExpressionParser.parse("var.blend * 0") instanceof CemExpression.Constant);
        assertFalse(CemExpressionParser.parse("nbt(SleepingX, exists:true)") instanceof CemExpression.Constant);
        assertEquals(CemAnimationProgram.method("random", new double[] {17}, null),
                CemExpressionParser.parse("random(17)").evaluate(null));
        assertEquals(0, CemExpressionParser.parse("false && nbt(Inventory, exists:true)").evaluate(null));
        assertEquals(1, CemExpressionParser.parse("true || missing").evaluate(null));
        assertEquals(6, CemExpressionParser.parse("if(true, 2 * 3, missing)").evaluate(null));
    }

    @Test void compiledMathMatchesGeneralEvaluation() {
        String unary = "sin cos tan asin acos atan sqrt abs frac log signum floor ceil round exp torad todeg wrapdeg wraprad";
        String binary = "atan2 pow fmod degdiff raddiff";
        String ternary = "lerp clamp between";
        for (String direction : List.of("in", "out", "inout")) {
            for (String curve : List.of("expo", "quad", "quart", "sine", "bounce", "cubic", "quint", "circ", "elastic", "back")) {
                ternary += " ease" + direction + curve;
            }
        }
        for (double x : new double[] {-2, -0.0, 0, 0.25, 0.5, 1, 2, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertFunctions(unary, new double[] {x});
            assertFunctions(binary, new double[] {x, 0.75});
            assertFunctions(ternary, new double[] {x, -0.5, 2.0});
        }
    }

    private static void assertFunctions(String names, double[] values) {
        List<CemExpression> args = new ArrayList<>();
        for (double value : values) args.add(context -> value);
        for (String name : names.split(" ")) {
            assertEquals(CemAnimationProgram.method(name, values, null),
                    CemAnimationProgram.compileMethod(name, args).evaluate(null), name);
        }
    }

    @Test void compiledArgumentsEvaluateOnceInOrder() {
        var calls = new ArrayList<Integer>();
        List<CemExpression> args = List.of(
                context -> { calls.add(0); return 0.25; },
                context -> { calls.add(1); return 0; },
                context -> { calls.add(2); return 1; });
        assertEquals(0.25, CemAnimationProgram.compileMethod("lerp", args).evaluate(null));
        assertEquals(List.of(0, 1, 2), calls);
    }

    @Test void primitiveFallbackRetainsVariableArityAndNestedCalls() {
        assertEquals(1, CemExpressionParser.parse("min(3, 2, 1, 4)").evaluate(null));
        assertEquals(4, CemExpressionParser.parse("max(3, 2, 1, 4)").evaluate(null));
        assertEquals(0, CemExpressionParser.parse("min()").evaluate(null));
        assertEquals(1, CemExpressionParser.parse("in(3, 1, 2, 3)").evaluate(null));
        assertEquals(7, CemExpressionParser.parse("catch(0/0, 7)").evaluate(null));
        assertEquals(0.25, CemExpressionParser.parse("sin(0) + pow(0.5, 2)").evaluate(null));
        assertEquals(15, CemExpressionParser.parse("keyframe(0.5, 10, 20)").evaluate(null));
        assertEquals(15, CemExpressionParser.parse("keyframeloop(0.5, 10, 20)").evaluate(null));
    }

    @Test void sleepingNbtTracksWakeupAndUsesExactVanillaIntegerCoordinates() {
        var sleeping = Optional.of(new BlockPos(-20, 72, 11));
        assertEquals(IntTag.valueOf(-20), CemAnimationProgram.sleepingCoordinate("SleepingX", sleeping));
        assertEquals(IntTag.valueOf(72), CemAnimationProgram.sleepingCoordinate("SleepingY", sleeping));
        assertEquals(IntTag.valueOf(11), CemAnimationProgram.sleepingCoordinate("SleepingZ", sleeping));
        assertNull(CemAnimationProgram.sleepingCoordinate("SleepingX", Optional.empty()));
        assertNull(CemAnimationProgram.sleepingCoordinate("sleepingx", sleeping));
    }

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
