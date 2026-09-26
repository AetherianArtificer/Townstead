package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Need;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Option;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionMergeTest {

    static ResourceLocation id(String s) {
        return com.aetherianartificer.townstead.data.DataPackLang.parseId(s);
    }

    static Option offer(String station, boolean available, List<Need> missing) {
        return Option.item(id("x:coat"), station, id("x:bench"), available,
                available ? "" : "Missing: things", 1, List.of(), missing);
    }

    static final Need STRING = new Need(List.of(id("minecraft:string")), 1, "");
    static final Need IRON = new Need(List.of(id("minecraft:iron_ingot")), 1, "");

    @Test
    void theRouteThatCanRunNowWinsAndBothStationsAreNamed() {
        Option merged = Option.merge(offer("Crafting Table", false, List.of(STRING, IRON)),
                offer("Sewing Table", true, List.of()));
        assertTrue(merged.available());
        assertTrue(merged.missing().isEmpty());
        assertEquals("Crafting Table, Sewing Table", merged.stationLabel());
    }

    @Test
    void whenNeitherCanRunTheShorterShortfallWinsAndTheFirstBreaksTies() {
        Option merged = Option.merge(offer("A", false, List.of(STRING, IRON)), offer("B", false, List.of(IRON)));
        assertFalse(merged.available());
        assertEquals(List.of(IRON), merged.missing());
        assertEquals("A, B", merged.stationLabel());

        Option tie = Option.merge(offer("A", false, List.of(IRON)), offer("B", false, List.of(STRING)));
        assertEquals(List.of(IRON), tie.missing());
    }

    @Test
    void theSameStationIsNotNamedTwice() {
        Option merged = Option.merge(offer("Bench", true, List.of()), offer("Bench", false, List.of(IRON)));
        assertEquals("Bench", merged.stationLabel());
        Option again = Option.merge(merged, offer("Bench", false, List.of()));
        assertEquals("Bench", again.stationLabel());
    }
}
