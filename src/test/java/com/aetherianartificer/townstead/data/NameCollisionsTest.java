package com.aetherianartificer.townstead.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The collision rule: only names that clash across namespaces are qualified, each by its own pack. */
class NameCollisionsTest {

    private static final Map<String, String> LANG = Map.of(
            "lineage.classic.high_elf", "High Elf",
            "pack.runic.qualifier", "Runic");

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void clashingNamesAreQualifiedPerPack() {
        Map<ResourceLocation, JsonElement> docs = new LinkedHashMap<>();
        docs.put(DataPackLang.parseId("townstead_classic:high_elf"), json("""
                {"display_name": {"translate": "lineage.classic.high_elf"},
                 "demonym": {"singular": "High Elf", "plural": "High Elves"}}"""));
        docs.put(DataPackLang.parseId("runic_roots:high_elf"), json("{\"display_name\": \"high elf \"}"));
        docs.put(DataPackLang.parseId("runic_roots:iron_one"), json("{\"display_name\": \"Iron One\"}"));
        docs.put(DataPackLang.parseId("runic_roots:moon_elf"), json("{\"display_name\": \"Moon Elf\"}"));
        docs.put(DataPackLang.parseId("runic_roots:moon_elf_alt"), json("{\"display_name\": \"Moon Elf\"}"));

        Set<ResourceLocation> qualified = NameCollisions.qualify("lineage", docs, LANG,
                ns -> ns.equals("runic_roots") ? json("{\"translate\": \"pack.runic.qualifier\"}") : null);

        assertEquals(Set.of(DataPackLang.parseId("townstead_classic:high_elf"),
                DataPackLang.parseId("runic_roots:high_elf")), qualified,
                "same-namespace duplicates and unique names stay plain");

        JsonObject classic = docs.get(DataPackLang.parseId("townstead_classic:high_elf")).getAsJsonObject();
        JsonObject name = classic.getAsJsonObject("display_name");
        assertEquals(NameCollisions.QUALIFIED_KEY, name.get("translate").getAsString());
        assertEquals(new JsonPrimitive("Classic"), name.getAsJsonArray("with").get(0),
                "no pack file: qualifier derives from the namespace");
        JsonObject plural = classic.getAsJsonObject("demonym").getAsJsonObject("plural");
        assertEquals("High Elves", plural.getAsJsonArray("with").get(1).getAsString());

        JsonObject runic = docs.get(DataPackLang.parseId("runic_roots:high_elf")).getAsJsonObject()
                .getAsJsonObject("display_name");
        assertEquals("pack.runic.qualifier",
                runic.getAsJsonArray("with").get(0).getAsJsonObject().get("translate").getAsString());
    }

    @Test
    void fileQualifierWins() {
        Map<ResourceLocation, JsonElement> docs = new LinkedHashMap<>();
        docs.put(DataPackLang.parseId("a:x"), json("{\"display_name\": \"Elf\", \"qualifier\": \"Wild\"}"));
        docs.put(DataPackLang.parseId("b:x"), json("{\"display_name\": \"Elf\"}"));
        NameCollisions.qualify("ancestry", docs, Map.of(), ns -> null);
        assertEquals("Wild", docs.get(DataPackLang.parseId("a:x")).getAsJsonObject()
                .getAsJsonObject("display_name").getAsJsonArray("with").get(0).getAsString());
    }

    @Test
    void defaultQualifierReadsNaturally() {
        assertEquals("Classic", NameCollisions.defaultQualifier("townstead_classic"));
        assertEquals("Runic Roots", NameCollisions.defaultQualifier("runic_roots"));
        assertEquals("Mythical", NameCollisions.defaultQualifier("mythical"));
    }

    @Test
    void qualifiedNameParsesWithArguments() {
        Map<String, String> lang = Map.of(NameCollisions.QUALIFIED_KEY, "%s %s", "q", "Runic");
        var component = DataPackLang.parseComponent(json(
                "{\"translate\": \"townstead.qualified_name\", \"with\": [{\"translate\": \"q\"}, \"High Elf\"]}"),
                "t", lang);
        var contents = (net.minecraft.network.chat.contents.TranslatableContents) component.getContents();
        assertEquals(NameCollisions.QUALIFIED_KEY, contents.getKey());
        assertEquals("Runic High Elf", contents.getFallback());
        assertEquals(2, contents.getArgs().length);
    }
}
