package com.aetherianartificer.townstead.pheno.action.block.types;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InsertItemBlockActionTypeTest {
    @Test
    void restockingNeverExceedsSupplyOrTargetAndStopsWhenAnotherWorkerFillsIt() {
        assertEquals(1, InsertItemBlockActionType.offerCount(64, 0, 1, 1));
        assertEquals(0, InsertItemBlockActionType.offerCount(64, 1, 1, 1));
        assertEquals(0, InsertItemBlockActionType.offerCount(64, 32, 1, 1));
        assertEquals(0, InsertItemBlockActionType.offerCount(0, 0, 1, 1));
        assertEquals(2, InsertItemBlockActionType.offerCount(2, 0, 8, 16));
        assertEquals(3, InsertItemBlockActionType.offerCount(64, 13, 8, 16));
    }

    @Test
    void rejectsUnsafeInventoryDeclarations() {
        var type = new InsertItemBlockActionType();
        for (String json : new String[]{"{}", "{\"slot\":-1}", "{\"slot\":0,\"count\":0}",
                "{\"slot\":0,\"limit\":0}", "{\"slot\":0,\"side\":\"invalid\"}",
                "{\"slot\":0,\"item\":\"\"}"}) {
            assertNull(type.parse(JsonParser.parseString(json).getAsJsonObject()), json);
        }
        assertNotNull(type.parse(JsonParser.parseString("{\"slot\":0}").getAsJsonObject()));
    }
}
