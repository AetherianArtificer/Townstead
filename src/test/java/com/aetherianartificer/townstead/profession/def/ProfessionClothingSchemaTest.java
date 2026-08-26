package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfessionClothingSchemaTest {

    @Test
    void acceptsOneClothingIdentityAsScalar() {
        ProfessionDef def = parse("{\"schema\":\"townstead:profession/v2\","
                + "\"clothing\":\"minecraft:farmer\"}");
        assertEquals(List.of(new ClothingChoice(id("minecraft:farmer"))), def.clothing());
    }

    @Test
    void preservesClothingFallbackOrder() {
        ProfessionDef def = parse("{\"schema\":\"townstead:profession/v2\","
                + "\"clothing\":[\"example:beekeeper\",\"minecraft:farmer\"]}");
        assertEquals(List.of(new ClothingChoice(id("example:beekeeper")),
                new ClothingChoice(id("minecraft:farmer"))), def.clothing());
    }

    @Test
    void acceptsHairCoverageOnOneFallback() {
        ProfessionDef def = parse("{\"schema\":\"townstead:profession/v2\","
                + "\"clothing\":[{\"id\":\"example:beekeeper\",\"hair\":\"covered\"},"
                + "\"minecraft:farmer\"]}");
        assertEquals(List.of(
                new ClothingChoice(id("example:beekeeper"), ClothingChoice.HairPolicy.COVERED),
                new ClothingChoice(id("minecraft:farmer"))), def.clothing());
    }

    private static ProfessionDef parse(String json) {
        return ProfessionDataLoader.parseProfession(id("example:beekeeper"),
                JsonParser.parseString(json).getAsJsonObject(), Map.of(), new Diagnostics());
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.tryParse(value);
    }
}
