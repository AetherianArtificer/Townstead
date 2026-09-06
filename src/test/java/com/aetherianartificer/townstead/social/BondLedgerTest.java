package com.aetherianartificer.townstead.social;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BondLedgerTest {
    @BeforeAll static void kinds() {
        BondKinds.replaceAll(Map.of(
                ResourceLocation.tryParse("test:friend"), new BondKind(ResourceLocation.tryParse("test:friend"), "", "Friend", 0, true, true, null),
                ResourceLocation.tryParse("test:marriage"), new BondKind(ResourceLocation.tryParse("test:marriage"), "", "Marriage", 1, true, true, null)));
    }

    @Test void recognizedBondSurvivesReloadAndOperationReplay() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(); BondLedger ledger = new BondLedger();
        assertTrue(ledger.form("friendship:a:b", "test:friend", a, "A", b, "B", 8, "test:earned"));
        assertFalse(ledger.form("friendship:a:b", "test:friend", a, "A", b, "B", 8, "test:earned"));
        assertFalse(ledger.form("different-operation", "test:friend", b, "B", a, "A", 9, "test:earned"));

        BondLedger loaded = BondLedger.load(ledger.save());
        assertEquals(1, loaded.size());
        assertEquals(b, loaded.bonds(a).get(0).other());
        assertTrue(loaded.bonds(a).get(0).active());
    }

    @Test void endRetainsHistoryAndExclusiveKindsRespectBothParticipants() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(); BondLedger ledger = new BondLedger();
        assertTrue(ledger.form("wedding-1", "test:marriage", a, "A", b, "B", 10, "test:wedding"));
        assertFalse(ledger.form("wedding-2", "test:marriage", c, "C", b, "B", 11, "test:wedding"));
        UUID id = ledger.entries(a).get(0).id();
        assertTrue(ledger.end(id, 20, "test:separation"));
        assertFalse(ledger.bonds(a).get(0).active());
        assertEquals(20, BondLedger.load(ledger.save()).bonds(a).get(0).endDay());
    }
}
