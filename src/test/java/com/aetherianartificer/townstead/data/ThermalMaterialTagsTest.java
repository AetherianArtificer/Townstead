package com.aetherianartificer.townstead.data;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ThermalMaterialTagsTest {
    private JsonArray values(String name) throws Exception {
        try (var stream=getClass().getClassLoader().getResourceAsStream("data/townstead/tags/" + blockTagDirectory() + "/thermal/"+name+".json")) {
            assertNotNull(stream);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("values");
        }
    }
    private String blockTagDirectory() {
        //? if >=1.21 {
        return "block";
        //?} else {
        /*return "blocks";
        *///?}
    }
    @Test void commonWallsAreNotClassedAsPurposeBuiltInsulation() throws Exception {
        var insulation=values("insulating_blocks");
        assertTrue(insulation.contains(new JsonPrimitive("#minecraft:wool")));
        assertFalse(insulation.contains(new JsonPrimitive("#minecraft:planks")));
        assertFalse(insulation.contains(new JsonPrimitive("#minecraft:logs")));
        assertFalse(insulation.contains(new JsonPrimitive("#minecraft:base_stone_overworld")));
        assertTrue(values("wood_walls").contains(new JsonPrimitive("#minecraft:planks")));
        assertTrue(values("glass_walls").contains(new JsonPrimitive("#minecraft:impermeable")));
        assertFalse(values("leaky_blocks").contains(new JsonPrimitive("#minecraft:impermeable")));
    }
}
