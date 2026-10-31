package com.aetherianartificer.townstead.politics.standing;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.value.types.StandingValueType;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandingTest {
    private static final SettlementRef MILLBROOK = new SettlementRef(DataPackLang.parseId("minecraft:overworld"), 3);
    private static final SettlementRef FENWICK = new SettlementRef(DataPackLang.parseId("minecraft:overworld"), 9);

    @Test
    void aDeedPaysOnceAndOnlyWhereItHappened() {
        DeedLedger ledger = new DeedLedger();
        UUID builder = UUID.randomUUID();

        assertTrue(ledger.credit(MILLBROOK, builder, "raised:4:bakery", 3));
        assertFalse(ledger.credit(MILLBROOK, builder, "raised:4:bakery", 3), "rebuilding the same building pays nothing");
        assertTrue(ledger.credit(MILLBROOK, builder, "raised:5:granary", 3));

        assertEquals(6, ledger.points(MILLBROOK, builder));
        assertEquals(0, ledger.points(FENWICK, builder));
    }

    @Test
    void standingAddsItsThreeSources() {
        assertEquals(34, new Standing(20, 9, 5).total());
        assertEquals(0, Standing.NONE.total());
    }

    @Test
    void standingValueNamesOnlyKnownSources() {
        StandingValueType type = new StandingValueType();

        assertNotNull(type.parse(JsonParser.parseString("{\"source\":\"hearts\"}").getAsJsonObject()));
        assertNotNull(type.parse(JsonParser.parseString("{}").getAsJsonObject()));
        assertNull(type.parse(JsonParser.parseString("{\"source\":\"bribes\"}").getAsJsonObject()));
    }
}
