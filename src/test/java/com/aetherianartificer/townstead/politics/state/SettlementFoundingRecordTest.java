package com.aetherianartificer.townstead.politics.state;

import com.aetherianartificer.townstead.data.DataPackLang;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SettlementFoundingRecordTest {
    @Test
    void aVillageFactionIdentityIncludesItsDimension() {
        SettlementRef overworld = new SettlementRef(id("minecraft:overworld"), 7);
        SettlementRef nether = new SettlementRef(id("minecraft:the_nether"), 7);

        assertNotEquals(PoliticalIds.villageFaction(overworld), PoliticalIds.villageFaction(nether));
        assertNotEquals(PoliticalIds.villageFaction(overworld),
                PoliticalIds.villageFaction(new SettlementRef(id("minecraft:overworld"), 8)));
    }

    private static ResourceLocation id(String value) {
        ResourceLocation parsed = DataPackLang.parseId(value);
        if (parsed == null) throw new IllegalArgumentException(value);
        return parsed;
    }
}
