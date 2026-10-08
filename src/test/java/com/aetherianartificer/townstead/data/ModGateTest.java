package com.aetherianartificer.townstead.data;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The "mods" gate grammar: strings and arrays require presence, any/all/not compose
 * arbitrarily, and malformed expressions evaluate to null so callers refuse the document.
 */
class ModGateTest {

    private static final Predicate<String> LOADED = Set.of("farmersdelight", "rusticdelight")::contains;

    private static Boolean eval(String json) {
        return ModGate.evaluate(JsonParser.parseString(json), LOADED);
    }

    @Test
    void stringAndArrayForms() {
        assertEquals(Boolean.TRUE, eval("\"farmersdelight\""));
        assertEquals(Boolean.FALSE, eval("\"chefsdelight\""));
        assertEquals(Boolean.TRUE, eval("[\"farmersdelight\", \"rusticdelight\"]"));
        assertEquals(Boolean.FALSE, eval("[\"farmersdelight\", \"chefsdelight\"]"));
    }

    @Test
    void objectClausesCompose() {
        assertEquals(Boolean.TRUE, eval("{\"any\": [\"chefsdelight\", \"rusticdelight\"]}"));
        assertEquals(Boolean.FALSE, eval("{\"any\": [\"chefsdelight\", \"vca\"]}"));
        assertEquals(Boolean.TRUE, eval("{\"not\": \"chefsdelight\"}"));
        assertEquals(Boolean.FALSE, eval("{\"not\": \"farmersdelight\"}"));
        assertEquals(Boolean.TRUE, eval(
                "{\"all\": [\"farmersdelight\", {\"any\": [\"rusticdelight\", \"vca\"]}], \"not\": \"conflict\"}"));
        assertEquals(Boolean.FALSE, eval(
                "{\"all\": [\"farmersdelight\"], \"not\": \"rusticdelight\"}"));
    }

    @Test
    void malformedExpressionsRefuse() {
        assertNull(eval("3"));
        assertNull(eval("{\"sometimes\": [\"farmersdelight\"]}"));
        assertNull(eval("[\"farmersdelight\", 3]"));
        assertNull(eval("{\"any\": \"farmersdelight\"}"), "any requires an array");
    }
}
