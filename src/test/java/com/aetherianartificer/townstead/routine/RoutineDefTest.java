package com.aetherianartificer.townstead.routine;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.ActionTypes;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.ConditionTypes;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoutineDefTest {

    private static final ResourceLocation ID = ResourceLocation.tryParse("examplepack:bathe");
    private static final String SPOT = "\"find\":{\"block\":{\"type\":\"constant\",\"value\":true}}";

    @BeforeAll
    static void register() {
        ConditionTypes.register(new ConditionType() {
            @Override public String key() { return "test:routine_yes"; }
            @Override public Condition parse(JsonObject json) { return ctx -> true; }
        });
        ActionTypes.register(new ActionType() {
            @Override public String key() { return "test:routine_noop"; }
            @Override public Action parse(JsonObject json) { return ctx -> {}; }
        });
    }

    private static RoutineDef parse(String text) {
        return RoutineDef.parse(ID, JsonParser.parseString(text).getAsJsonObject());
    }

    @Test
    void aSingleStepWithAPlaceIsEnough() {
        RoutineDef def = parse("{\"steps\":[{" + SPOT + "}]}");
        assertEquals(1, def.steps().size());
        RoutineDef.Option option = def.steps().get(0).options().get(0);
        assertEquals(2, option.reach());
        assertFalse(option.inFluid());
        assertTrue(option.stay().endsAtOnce());
        assertTrue(def.claim());
        assertEquals(2400, def.maxTicks());
    }

    @Test
    void optionsTakeTheStepsStayRulesUnlessTheyHaveTheirOwn() {
        RoutineDef def = parse("""
                {"priority":40,"condition":{"type":"test:routine_yes"},
                 "progress":{"value":1,"direction":"down"},
                 "steps":[{"until":{"type":"test:routine_yes"},"stalled":400,
                   "options":[
                     {"worth":40,%s,"arrive":"in_fluid"},
                     {"worth":12,%s,"every":40,"times":4,"action":{"type":"test:routine_noop"}}]}],
                 "finish":{"min_progress":0.15,"action":{"type":"test:routine_noop"}}}
                """.formatted(SPOT, SPOT));
        var options = def.steps().get(0).options();
        assertTrue(options.get(0).inFluid());
        assertNotNull(options.get(0).stay().until());
        assertEquals(400, options.get(0).stay().stalled());
        assertEquals(4, options.get(1).stay().times());
        assertEquals(40, options.get(1).stay().every());
        assertNotNull(options.get(1).stay().until(), "an option keeps the step's until");
        assertTrue(def.progress().down());
        assertEquals(0.15, def.finish().minProgress());
    }

    @Test
    void elseCanStopSkipOrAct() {
        assertEquals(RoutineDef.Else.STOP, parse("{\"steps\":[{" + SPOT + "}]}").steps().get(0).orElse());
        assertEquals(RoutineDef.Else.SKIP, parse("{\"steps\":[{" + SPOT + ",\"else\":\"skip\"}]}").steps().get(0).orElse());
        var acting = parse("{\"steps\":[{" + SPOT + ",\"else\":[{\"type\":\"test:routine_noop\"}]}]}").steps().get(0);
        assertEquals(RoutineDef.Else.ACTIONS, acting.orElse());
        assertNotNull(acting.elseAction());
    }

    @Test
    void aStepCanRepeatAtEachPlaceItFinds() {
        assertTrue(parse("{\"steps\":[{" + SPOT + ",\"repeat\":true}]}").steps().get(0).repeat());
        assertFalse(parse("{\"steps\":[{" + SPOT + "}]}").steps().get(0).repeat());
    }

    @Test
    void aLaterStepCanComeBackToANamedPlace() {
        RoutineDef def = parse("{\"steps\":[{" + SPOT + ",\"name\":\"basin\"},{\"find\":{\"named\":\"basin\"}}],\"end\":[{\"type\":\"test:routine_noop\"}]}");
        assertEquals("basin", def.steps().get(0).name());
        assertEquals("basin", def.steps().get(1).options().get(0).named());
        assertNull(def.steps().get(1).options().get(0).block());
        assertNotNull(def.end());
        assertThrows(IllegalArgumentException.class, () -> parse("{\"steps\":[{\"find\":{\"named\":\"basin\"}},{" + SPOT + ",\"name\":\"basin\"}]}"));
    }

    @Test
    void rejectsWhatCannotRun() {
        assertThrows(IllegalArgumentException.class, () -> parse("{\"steps\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"steps\":[{\"until\":{\"type\":\"test:routine_yes\"}}]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"steps\":[{" + SPOT + ",\"arrive\":\"sit\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"steps\":[{" + SPOT + ",\"else\":\"later\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"steps\":[{" + SPOT + ",\"times\":-1}]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"steps\":[{" + SPOT + "}],\"when\":1}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"steps\":[{" + SPOT + "}],\"max_ticks\":5}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"steps\":[{\"find\":{}}]}"));
    }
}
