package com.aetherianartificer.townstead.chronicle.template;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;

class ChronicleEventRegistryTest {

    @Test
    void unknownNonLegacyIdReturnsNull() {
        ChronicleEventRegistry.replaceAll(Map.of());

        assertNull(ChronicleEventRegistry.byId(
                ResourceLocation.tryParse("townstead:debug_dump")));
    }
}
