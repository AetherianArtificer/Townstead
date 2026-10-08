package com.aetherianartificer.townstead.clothing;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyClothingTest {

    static ResourceLocation id(String s) {
        return com.aetherianartificer.townstead.data.DataPackLang.parseId(s);
    }

    @Test
    void parsesArrayAndObjectForms() {
        BodyClothing list = BodyClothing.parse(JsonParser.parseString(
                "{ \"body_clothing\": [\"a:one\", \"a:two\"] }").getAsJsonObject());
        assertEquals(List.of(id("a:one"), id("a:two")), list.sets());
        assertFalse(list.replace());

        BodyClothing object = BodyClothing.parse(JsonParser.parseString(
                "{ \"body_clothing\": { \"replace\": true, \"sets\": \"a:three\" } }").getAsJsonObject());
        assertEquals(List.of(id("a:three")), object.sets());
        assertTrue(object.replace());

        assertSame(BodyClothing.INHERIT, BodyClothing.parse(JsonParser.parseString("{}").getAsJsonObject()));
        assertSame(BodyClothing.INHERIT, BodyClothing.parse(null));
    }

    @Test
    void chainAccumulatesUntilANodeReplaces() {
        BodyClothing species = new BodyClothing(List.of(id("a:species")), false);
        BodyClothing ancestry = new BodyClothing(List.of(id("a:ancestry"), id("a:species")), false);
        BodyClothing lineage = new BodyClothing(List.of(id("a:lineage")), true);

        BodyClothing twoLevels = BodyClothing.INHERIT.mergedWith(species).mergedWith(ancestry);
        assertEquals(List.of(id("a:species"), id("a:ancestry")), twoLevels.sets());

        BodyClothing replaced = twoLevels.mergedWith(lineage);
        assertEquals(List.of(id("a:lineage")), replaced.sets());
        assertFalse(replaced.replace());

        assertSame(twoLevels, twoLevels.mergedWith(BodyClothing.INHERIT));
        assertSame(twoLevels, twoLevels.mergedWith(null));
    }
}
