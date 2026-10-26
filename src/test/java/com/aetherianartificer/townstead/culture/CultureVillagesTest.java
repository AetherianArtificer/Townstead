package com.aetherianartificer.townstead.culture;

import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CultureVillagesTest {
    private static final ResourceLocation CULTURE = ResourceLocation.tryParse("test:rosaguarda");
    private static final ResourceLocation TOWN = ResourceLocation.tryParse("test:village/town");
    private static final ResourceLocation CAMP = ResourceLocation.tryParse("test:village/freeblade_camp");

    @AfterEach
    void clear() {
        CultureVillages.replaceCultures(Map.of());
        CultureVillages.replaceProfiles(Map.of());
        Cultures.replace(Map.of());
    }

    @Test
    void readsVillageStructureIds() {
        var json = JsonParser.parseString("{\"villages\": [\"test:village/town\", \"test:village/freeblade_camp\"]}")
                .getAsJsonObject();
        assertEquals(List.of(TOWN, CAMP), CultureJsonLoader.villages(json));
        assertTrue(CultureJsonLoader.villages(JsonParser.parseString("{}").getAsJsonObject()).isEmpty());
    }

    @Test
    void rejectsMalformedVillageIds() {
        var json = JsonParser.parseString("{\"villages\": [\"Not An Id\"]}").getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> CultureJsonLoader.villages(json));
    }

    @Test
    void mapsStructuresToTheirCulture() {
        Cultures.replace(Map.of(CULTURE, new Culture(CULTURE, Component.literal("Rosaguarda"), null)));
        CultureVillages.replaceCultures(Map.of(TOWN, CULTURE));

        assertEquals(CULTURE, CultureVillages.culture(TOWN));
        assertNull(CultureVillages.culture(CAMP));
        assertTrue(CultureVillages.profiles(TOWN).isEmpty());
    }
}
