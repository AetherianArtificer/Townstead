package com.aetherianartificer.townstead.story;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StoryTextTest {
    @Test
    void buildingTokensBecomeTranslatedNames() {
        String line = "You put up the " + StoryText.building("compat/farmersdelight/kitchen_l1") + ", right?";
        assertEquals("You put up the Kitchen, right?",
                StoryText.resolve(line, key -> key.equals("buildingType.compat/farmersdelight/kitchen_l1") ? "Kitchen" : key));
    }

    @Test
    void textWithoutTokensIsUntouched() {
        assertEquals("No [brackets] here.", StoryText.resolve("No [brackets] here.", key -> "x"));
    }
}
