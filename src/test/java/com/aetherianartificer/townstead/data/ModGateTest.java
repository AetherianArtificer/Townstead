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

    private static final ModGate.Installed BUILD = new ModGate.Installed() {
        @Override
        public String version(String mod) {
            return switch (mod) {
                case "farmersdelight" -> "1.2.5-neoforge";
                case "minecraft" -> "1.21.1";
                default -> null;
            };
        }

        @Override
        public Boolean block(String id, String property) {
            if (!id.contains(":")) return null;
            return id.equals("examplemod:machine") && (property == null || property.equals("stirred"));
        }
    };

    private static Boolean evalBuild(String json) {
        return ModGate.evaluate(JsonParser.parseString(json), LOADED, NAMESPACES, BUILD);
    }

    @Test
    void versionClauses() {
        assertEquals(Boolean.TRUE, evalBuild("{\"mod\": \"farmersdelight\", \"version\": \">=1.2.5\"}"));
        assertEquals(Boolean.TRUE, evalBuild("{\"mod\": \"farmersdelight\", \"version\": \">=1.2 <2\"}"));
        assertEquals(Boolean.FALSE, evalBuild("{\"mod\": \"farmersdelight\", \"version\": \"<1.2.5\"}"));
        assertEquals(Boolean.FALSE, evalBuild("{\"mod\": \"chefsdelight\", \"version\": \">=0\"}"));
        assertEquals(Boolean.TRUE, evalBuild("{\"not\": {\"mod\": \"farmersdelight\", \"version\": \"<1\"}}"));
        assertEquals(Boolean.TRUE, evalBuild("{\"minecraft\": \">=1.21\"}"));
        assertEquals(Boolean.FALSE, evalBuild("{\"minecraft\": \"1.20.1\"}"));
        assertNull(evalBuild("{\"version\": \">=1\"}"), "a version needs a mod");
        assertNull(evalBuild("{\"mod\": [\"farmersdelight\"], \"version\": \">=1\"}"));
        assertNull(evalBuild("{\"mod\": \"chefsdelight\", \"version\": \"soon\"}"),
                "a bad range is malformed even when the mod is absent");
        assertNull(evalBuild("{\"minecraft\": 3}"));
    }

    @Test
    void versionOrdering() {
        assertTrue(VersionRange.parse("<1.0").contains("1.0-beta.2"));
        assertTrue(VersionRange.parse("1.0").contains("1.0.0"));
        assertTrue(VersionRange.parse("1.0").contains("1.0+build.7"));
        assertTrue(VersionRange.parse(">2.1.9").contains("2.1.11"));
        assertTrue(VersionRange.parse(">=1.20.1-1.3").contains("1.20.1-1.3.4"));
        assertFalse(VersionRange.parse(">=2.2").contains("2.1.11"));
        assertNull(VersionRange.parse(""));
        assertNull(VersionRange.parse(">=latest"));
    }

    @Test
    void blockClauses() {
        assertEquals(Boolean.TRUE, evalBuild("{\"block\": \"examplemod:machine\"}"));
        assertEquals(Boolean.TRUE, evalBuild("{\"block\": \"examplemod:machine\", \"property\": \"stirred\"}"));
        assertEquals(Boolean.FALSE, evalBuild("{\"block\": \"examplemod:machine\", \"property\": \"shaken\"}"));
        assertEquals(Boolean.FALSE, evalBuild("{\"block\": \"examplemod:absent\"}"));
        assertEquals(Boolean.TRUE, evalBuild("{\"not\": {\"block\": \"examplemod:absent\"}}"));
        assertNull(evalBuild("{\"property\": \"stirred\"}"), "a property needs a block");
        assertNull(evalBuild("{\"block\": 3}"));
        assertNull(evalBuild("{\"not\": {\"block\": \"unknowable\"}}"),
                "before blocks register, a negated clause must not read as met");
    }

    @Test
    void malformedExpressionsRefuse() {
        assertNull(eval("3"));
        assertNull(eval("{\"sometimes\": [\"farmersdelight\"]}"));
        assertNull(eval("[\"farmersdelight\", 3]"));
        assertNull(eval("{\"any\": \"farmersdelight\"}"), "any requires an array");
    }

    private static final Predicate<String> NAMESPACES = Set.of("townstead_classic")::contains;

    private static Boolean evalWithData(String json) {
        return ModGate.evaluate(JsonParser.parseString(json), LOADED, NAMESPACES);
    }

    @Test
    void namespaceAndModClauses() {
        assertEquals(Boolean.TRUE, evalWithData("{\"namespace\": \"townstead_classic\"}"));
        assertEquals(Boolean.FALSE, evalWithData("{\"namespace\": \"runic_roots\"}"));
        assertEquals(Boolean.FALSE, evalWithData("{\"namespace\": [\"townstead_classic\", \"runic_roots\"]}"));
        assertEquals(Boolean.TRUE, evalWithData("{\"mod\": \"farmersdelight\", \"namespace\": \"townstead_classic\"}"));
        assertEquals(Boolean.FALSE, evalWithData("{\"not\": {\"namespace\": \"townstead_classic\"}}"));
        assertEquals(Boolean.TRUE, evalWithData(
                "{\"any\": [{\"namespace\": \"runic_roots\"}, \"rusticdelight\"]}"));
        assertNull(evalWithData("{\"namespace\": 3}"));
        assertNull(evalWithData("{\"namespace\": [\"a\", 3]}"));
    }

    @Test
    void documentGatesCombineModsAndRequires() {
        DataNamespaces.setForTesting(Set.of("townstead_classic"));
        try {
            assertEquals(Boolean.TRUE, ModGate.evaluateDocument(JsonParser.parseString("{}").getAsJsonObject()));
            assertEquals(Boolean.TRUE, ModGate.evaluateDocument(JsonParser.parseString(
                    "{\"requires\": {\"namespace\": \"townstead_classic\"}}").getAsJsonObject()));
            assertEquals(Boolean.FALSE, ModGate.evaluateDocument(JsonParser.parseString(
                    "{\"requires\": {\"namespace\": \"runic_roots\"}}").getAsJsonObject()));
            assertNull(ModGate.evaluateDocument(JsonParser.parseString(
                    "{\"requires\": {\"sometimes\": 1}}").getAsJsonObject()));
        } finally {
            DataNamespaces.setForTesting(Set.of());
        }
    }
}
