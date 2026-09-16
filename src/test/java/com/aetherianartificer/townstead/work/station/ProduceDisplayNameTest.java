package com.aetherianartificer.townstead.work.station;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProduceDisplayNameTest {

    static ResourceLocation id(String s) {
        return com.aetherianartificer.townstead.data.DataPackLang.parseId(s);
    }

    static WorkstationDef.Produce line(String copies, String modifies, String name) {
        return new WorkstationDef.Produce(List.of("x:in"), null, 0, id("x:out"), 1, 100,
                copies == null ? null : id(copies), modifies, name);
    }

    @Test
    void aDeclaredNameIsTheKey() {
        assertEquals("townstead.produce.sew_onto_armor",
                line(null, "armor", "townstead.produce.sew_onto_armor").labelKey());
        assertEquals("x.engrave", line("minecraft:filled_map", null, "x.engrave").labelKey());
    }

    @Test
    void withoutANameTheKindOfLineChoosesTheKey() {
        assertEquals(WorkstationDef.Produce.COPY_KEY, line("minecraft:filled_map", null, null).labelKey());
        assertEquals(WorkstationDef.Produce.ON_HELD_ARMOR_KEY, line(null, "armor", null).labelKey());
        assertEquals(WorkstationDef.Produce.ON_HELD_KEY, line(null, "minecraft:iron_sword", null).labelKey());
        assertEquals(WorkstationDef.Produce.ON_HELD_KEY, line(null, "#minecraft:swords", null).labelKey());
        assertNull(line(null, null, null).labelKey());
        assertNull(line(null, null, "  ").labelKey());
    }
}
