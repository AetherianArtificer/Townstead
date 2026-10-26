package com.aetherianartificer.townstead.social;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RelationshipLedgerTest {
    private static final UUID ADA = UUID.randomUUID(), BRAM = UUID.randomUUID();

    @BeforeAll static void definitions() {
        RelationshipQualities.replaceAll(Map.of(
                id("test:affection"), quality("test:affection", -100, 100, 0, 10),
                id("test:trust"), quality("test:trust", -100, 100, 0, 0),
                id("test:resentment"), quality("test:resentment", 0, 100, 0, 20)));
    }

    @Test void preservesAmbivalenceAndDirection() {
        RelationshipLedger ledger = new RelationshipLedger();
        assertTrue(ledger.apply(ADA, BRAM, change("love", "test:affection", 70, 0, 0)));
        assertTrue(ledger.apply(ADA, BRAM, change("betrayal", "test:trust", -60, 0, 0)));
        assertTrue(ledger.apply(ADA, BRAM, change("betrayal", "test:resentment", 80, 0, 20)));
        assertTrue(ledger.apply(BRAM, ADA, change("distance", "test:affection", -20, 0, 0)));

        assertEquals(70, ledger.value(ADA, BRAM, "test:affection", 30), 0.001);
        assertEquals(-60, ledger.value(ADA, BRAM, "test:trust", 30), 0.001);
        assertEquals(40, ledger.value(ADA, BRAM, "test:resentment", 20), 0.001);
        assertEquals(-20, ledger.value(BRAM, ADA, "test:affection", 30), 0.001);
    }

    @Test void operationsAreRepeatSafeAndSurviveSaveLoad() {
        RelationshipLedger ledger = new RelationshipLedger();
        var contribution = change("conversation:1", "test:affection", 8, 4, 10);
        assertTrue(ledger.apply(ADA, BRAM, contribution));
        assertFalse(ledger.apply(ADA, BRAM, contribution));
        assertEquals(1, ledger.contributionCount());

        RelationshipLedger loaded = RelationshipLedger.load(ledger.save());
        assertEquals(1, loaded.contributionCount());
        assertEquals(4, loaded.value(ADA, BRAM, "test:affection", 14), 0.001);
        assertFalse(loaded.apply(ADA, BRAM, contribution));
    }

    @Test void parserReportsBadAuthoringAndMissingPacksPreserveIds() {
        var valid = JsonParser.parseString("""
                {"schema":"townstead:relationship_quality/v1","display":{"text":"Loyalty"},
                 "range":{"min":-50,"max":50,"neutral":0},
                 "decay":{"default_half_life_days":30,"prune_below":0.1}}
                """).getAsJsonObject();
        RelationshipQuality parsed = RelationshipQuality.parse(id("test:loyalty"), valid, Map.of());
        assertEquals("Loyalty", parsed.displayLiteral());
        assertEquals(30, parsed.defaultHalfLifeDays());

        var invalid = valid.deepCopy(); invalid.addProperty("typo", true);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> RelationshipQuality.parse(id("test:loyalty"), invalid, Map.of())).getMessage().contains("typo"));
        assertEquals("missing", RelationshipQualities.byId("test:missing").displayLiteral());
    }

    private static RelationshipLedger.Contribution change(String operation, String quality, float amount, long day, int halfLife) {
        return new RelationshipLedger.Contribution(operation, quality, amount, day, halfLife, "test:scenario");
    }
    private static RelationshipQuality quality(String id, float min, float max, float neutral, int halfLife) {
        return new RelationshipQuality(id(id), "", id, min, max, neutral, halfLife, 0.01f);
    }
    private static ResourceLocation id(String value) { return ResourceLocation.tryParse(value); }
}
