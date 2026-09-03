package com.aetherianartificer.townstead.work;

import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutputAppraisalDefTest {
    @AfterEach void clear() { OutputAppraisals.replaceForTest(List.of()); }

    @Test
    void mapsExactNativeDataWithoutInventingQuality() {
        OutputAppraisalDef definition = parse("""
                {"schema":"townstead:output_appraisal/v1",
                 "path":["brewery.beer_quality"],
                 "tiers":[
                   {"value":0,"quality":1,"label":"poor","order_worthy":false},
                   {"min":1,"max":2,"quality":2,"label":"fine"}
                 ]}
                """);
        assertNotNull(definition);
        CompoundTag data = new CompoundTag();
        data.putInt("brewery.beer_quality", 0);
        assertEquals(0.0d, OutputAppraisalDef.numericValue(data,
                List.of("brewery.beer_quality")));
        assertFalse(definition.tiers().get(0).orderWorthy());
        assertEquals("fine", definition.tiers().get(1).label());
    }

    @Test
    void appraisesTheExactProducedStack() {
        OutputAppraisalDef definition = parse("""
                {"schema":"townstead:output_appraisal/v1","items":"minecraft:potion",
                 "path":["BrewLevel"],
                 "tiers":[{"value":4,"quality":3,"label":"fine"}]}
                """);
        assertNotNull(definition);
        OutputAppraisal.Appraisal appraisal = definition.appraise(4);
        assertNotNull(appraisal);
        assertEquals(3, appraisal.quality());
        assertEquals("fine", appraisal.label());
        assertEquals(ResourceLocation.tryParse("test:quality"), appraisal.source());
    }

    @Test
    void rejectsAmbiguousOrUnsafeMappings() {
        assertNull(parse("{\"path\":[],\"tiers\":[]}"));
        assertNull(parse("""
                {"path":["quality"],"tiers":[{"min":3,"max":1,"quality":2,"label":"bad"}]}
                """));
        assertNull(parse("""
                {"path":["quality"],"tiers":[{"value":1,"quality":0,"label":"bad"}]}
                """));
    }

    private static OutputAppraisalDef parse(String json) {
        return OutputAppraisalDef.parse(ResourceLocation.tryParse("test:quality"),
                JsonParser.parseString(json).getAsJsonObject());
    }
}
