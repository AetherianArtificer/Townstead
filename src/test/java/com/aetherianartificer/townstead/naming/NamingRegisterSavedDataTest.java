package com.aetherianartificer.townstead.naming;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NamingRegisterSavedDataTest {
    @Test
    void villageCulturesAreScopedByDimension() {
        NamingRegisterSavedData data = new NamingRegisterSavedData();
        ResourceLocation overworld = ResourceLocation.tryParse("minecraft:overworld");
        ResourceLocation nether = ResourceLocation.tryParse("minecraft:the_nether");

        data.putVillageCulture(overworld, 7, "example:deepers");
        data.putVillageCulture(nether, 7, "example:nether_covenant");

        assertEquals("example:deepers", data.villageCulture(overworld, 7));
        assertEquals("example:nether_covenant", data.villageCulture(nether, 7));
        assertEquals("example:deepers", data.villageCulture(7),
                "legacy access remains an Overworld view");
    }
}
