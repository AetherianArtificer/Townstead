package com.aetherianartificer.townstead.hunger.diet;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DietTest {
    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }

    private static Diet parse(String name, String json) {
        return Diet.parse(id("townstead:" + name), JsonParser.parseString(json).getAsJsonObject());
    }

    private static Diet bundled(String name) {
        var stream = DietTest.class.getResourceAsStream("/data/townstead/diet/" + name + ".json");
        assertNotNull(stream, name);
        return Diet.parse(id("townstead:" + name),
                JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
    }

    private static final ResourceLocation BEEF = id("minecraft:beef");
    private static final ResourceLocation BREAD = id("minecraft:bread");
    private static final ResourceLocation DIAMOND = id("minecraft:diamond");
    private static final Predicate<ResourceLocation> BEEF_TAGS = tag -> tag.equals(id("townstead:orders/raw_meats"));
    private static final Predicate<ResourceLocation> NO_TAGS = tag -> false;

    @Test
    void omnivoreAcceptsEveryNativeFoodAndNothingElse() {
        List<Diet.Food> foods = bundled("omnivore").foods();
        assertTrue(Diets.nourishment(foods, BEEF, BEEF_TAGS, true).nativeValues());
        assertNotNull(Diets.nourishment(foods, BREAD, NO_TAGS, true));
        assertNull(Diets.nourishment(foods, DIAMOND, NO_TAGS, false));
    }

    @Test
    void carnivoreAndHerbivoreSplitOnMeatTags() {
        List<Diet.Food> carnivore = bundled("carnivore").foods();
        List<Diet.Food> herbivore = bundled("herbivore").foods();
        assertNotNull(Diets.nourishment(carnivore, BEEF, BEEF_TAGS, true));
        assertNull(Diets.nourishment(carnivore, BREAD, NO_TAGS, true));
        assertNull(Diets.nourishment(herbivore, BEEF, BEEF_TAGS, true));
        assertNotNull(Diets.nourishment(herbivore, BREAD, NO_TAGS, true));
    }

    @Test
    void authoredFoodsFeedItemsWithoutNativeValues() {
        Diet lithovore = parse("lithovore", """
                {"schema":"townstead:diet/v1","foods":[
                  {"items":["#c:gems"],"nutrition":6,"saturation":0.8,"remainder":"minecraft:cobblestone"}]}
                """);
        Diets.Nourishment gem = Diets.nourishment(lithovore.foods(), DIAMOND, tag -> tag.equals(id("c:gems")), false);
        assertNotNull(gem);
        assertFalse(gem.nativeValues());
        assertEquals(6, gem.nutrition());
        assertEquals(0.8f, gem.saturation());
        assertEquals("minecraft:cobblestone", gem.remainder().toString());
        assertNull(Diets.nourishment(lithovore.foods(), BREAD, NO_TAGS, true));
    }

    @Test
    void nativeEntriesNeverAcceptItemsWithoutFoodValues() {
        Diet diet = parse("odd", """
                {"schema":"townstead:diet/v1","foods":[{"items":["minecraft:diamond"],"nutrition":"native"}]}
                """);
        assertNull(Diets.nourishment(diet.foods(), DIAMOND, NO_TAGS, false));
    }

    @Test
    void includesFlattenAfterOwnFoodsAndCyclesAreCut() {
        Map<ResourceLocation, Diet> loaded = new LinkedHashMap<>();
        loaded.put(id("townstead:a"), parse("a", """
                {"schema":"townstead:diet/v1","foods":[{"items":["minecraft:diamond"],"nutrition":2}],
                 "includes":["townstead:b"]}
                """));
        loaded.put(id("townstead:b"), parse("b", """
                {"schema":"townstead:diet/v1","foods":[{"items":["minecraft:bread"],"nutrition":"native"}],
                 "includes":["townstead:a"]}
                """));
        List<Diet.Food> flat = Diets.flatten(loaded).get(id("townstead:a"));
        assertEquals(2, flat.size());
        assertEquals(Set.of(DIAMOND), flat.get(0).items());
        assertNotNull(Diets.nourishment(flat, BREAD, NO_TAGS, true));
    }

    @Test
    void bareDietWordsNameBuiltInFiles() {
        assertEquals(id("townstead:carnivore"), Diets.idOf("carnivore"));
        assertEquals(id("mypack:lithovore"), Diets.idOf("mypack:lithovore"));
        assertNull(Diets.idOf(" "));
    }

    @Test
    void rejectsMalformedDiets() {
        assertThrows(IllegalArgumentException.class, () -> parse("x", """
                {"schema":"townstead:diet/v1","foods":[{"nutrition":2}]}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("x", """
                {"schema":"townstead:diet/v1","foods":[{"items":["minecraft:diamond"],"nutrition":"lots"}]}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("x", """
                {"schema":"townstead:diet/v1","foods":[{"items":["minecraft:bread"],"exclude":["*"]}]}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("x", "{\"schema\":\"townstead:diet/v1\"}"));
    }
}
