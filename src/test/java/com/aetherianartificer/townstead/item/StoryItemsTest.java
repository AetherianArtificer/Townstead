package com.aetherianartificer.townstead.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class StoryItemsTest {
    private static JsonObject read(String name) throws Exception {
        try (var in = StoryItemsTest.class.getResourceAsStream("/data/townstead/story_item/" + name + ".json")) {
            assertNotNull(in, name);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void shippedStoryItemsParse() throws Exception {
        StoryItems.Definition signet = StoryItems.parse(ResourceLocation.tryParse("townstead:court_signet"), read("court_signet"));
        assertEquals(ResourceLocation.tryParse("minecraft:gold_nugget"), signet.base());
        StoryItems.Definition book = StoryItems.parse(ResourceLocation.tryParse("townstead:book_of_oaths"), read("book_of_oaths"));
        assertNotNull(book.bookTitle());
        assertEquals(7, book.pages().size());
    }

    @Test
    void bookNeedsWrittenBookBase() {
        JsonObject json = JsonParser.parseString("""
                { "schema": "townstead:story_item/v1", "base": "minecraft:paper", "name": "Letter",
                  "book": { "title": "T", "pages": ["p"] } }""").getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> StoryItems.parse(ResourceLocation.tryParse("townstead:bad"), json));
    }
}
