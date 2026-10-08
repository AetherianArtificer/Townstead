package com.aetherianartificer.townstead.culture;

import com.google.gson.JsonParser;
import net.conczin.mca.resources.WeightedPool;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementNamingTest {
    private static final ResourceLocation CULTURE = ResourceLocation.tryParse("test:rosaguarda");
    private static final ResourceLocation POOL = ResourceLocation.tryParse("test:rosaguarda_places");

    @AfterEach
    void clearRegistries() {
        Cultures.replace(Map.of());
        SettlementNamePools.replace(Map.of());
    }

    @Test
    void cultureReplacesAProvisionalMcaNameAndKeepsItsResolvedNameStable() {
        WeightedPool.Mutable<SettlementNamePool.Form> names = new WeightedPool.Mutable<>(
                new SettlementNamePool.Form("", Map.of()));
        names.add(new SettlementNamePool.Form("Valecourt", Map.of()), 1.0F);
        SettlementNamePools.replace(Map.of(POOL,
                new SettlementNamePool(POOL, names, Set.of("Valecourt"))));
        Cultures.replace(Map.of(CULTURE,
                new Culture(CULTURE, Component.literal("Rosaguarda"), null, POOL, CultureClothing.NONE)));

        assertEquals("Valecourt", SettlementNaming.resolve(CULTURE, "Baystrand"));
        assertEquals("Valecourt", SettlementNaming.resolve(CULTURE, "Valecourt"));
    }

    @Test
    void settlementNameListsAcceptFlatAndWeightedJson() {
        assertEquals(Set.of("Rosewatch", "Bellmere"), SettlementNameJsonLoader.values(
                JsonParser.parseString("[\"Rosewatch\", \"Bellmere\", \"\"]")));
        assertEquals(Set.of("Rosewatch"), SettlementNameJsonLoader.values(
                JsonParser.parseString("{\"Rosewatch\": 4, \"Forgotten\": 0}")));
    }

    @Test
    void compositionalPatternsExpandAndRemainRecognizable() {
        var parsed = SettlementNameJsonLoader.parse(POOL, JsonParser.parseString("""
                {
                  "parts": { "first": ["Averaine", "Bellac"], "last": ["Vaux", "Egle"] },
                  "patterns": { "{first} de {last}": 8, "Court d'{last}": 2 }
                }
                """).getAsJsonObject());

        assertEquals(Set.of("Averaine de Vaux", "Averaine de Egle", "Bellac de Vaux",
                "Bellac de Egle", "Court d'Vaux", "Court d'Egle"), parsed.values());
    }
}
