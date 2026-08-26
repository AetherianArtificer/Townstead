package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The flat-careers relationship layer: Combo Skills join two or more leveled careers
 * laterally, and build titles rename a profession from its completed skill build
 * ("Rotisseur (Cook)"). Both are derived from history, never stored.
 */
class ComboAndTitleTest {

    @AfterEach
    void reset() {
        ProfessionTitles.replaceAll(Map.of());
    }

    @Test
    void comboSkillParsesThresholdsAndGrants() {
        ComboSkillDef def = ComboSkillDef.parse(id("townstead:charcutier"), JsonParser.parseString("""
                {"professions": {"townstead:cook": 3, "minecraft:butcher": 2},
                 "display_name": {"text": "Charcutier"},
                 "grants": [{"capability": "townstead:cook_xp_flat", "op": "add", "value": 1}]}""")
                .getAsJsonObject(), Map.of(), new Diagnostics());
        assertNotNull(def);
        assertEquals(2, def.thresholds().size());
        assertEquals(3, def.thresholds().get(id("townstead:cook")));
        assertEquals(1, def.grants().size());
    }

    @Test
    void comboSkillNeedsAtLeastTwoCareers() {
        assertNull(ComboSkillDef.parse(id("townstead:solo"), JsonParser.parseString("""
                {"professions": {"townstead:cook": 3}}""").getAsJsonObject(),
                Map.of(), new Diagnostics()),
                "one career at level N is an ordinary skill, not a combo");
        assertNull(ComboSkillDef.parse(id("townstead:bad"), JsonParser.parseString("""
                {"professions": {"townstead:cook": 0, "minecraft:butcher": 2}}""").getAsJsonObject(),
                Map.of(), new Diagnostics()),
                "thresholds below level 1 are malformed");
    }

    @Test
    void titleResolvesBestCompletedBuild() {
        ResourceLocation cook = id("townstead:cook");
        ProfessionTitles.replaceAll(Map.of(cook, List.of(
                new ProfessionTitles.Title(cook, "rotisseur", Component.literal("Rotisseur"),
                        List.of(id("townstead:cook/open_flame"))),
                new ProfessionTitles.Title(cook, "pitmaster", Component.literal("Pitmaster"),
                        List.of(id("townstead:cook/open_flame"), id("townstead:cook/cast_iron_stomach"))))));

        Set<ResourceLocation> learned = Set.of(id("townstead:cook/open_flame"));
        ProfessionTitles.Title title = ProfessionTitles.resolve(cook, learned::contains);
        assertNotNull(title);
        assertEquals("rotisseur", title.id(), "only the one-skill build is complete");

        Set<ResourceLocation> mastered = Set.of(
                id("townstead:cook/open_flame"), id("townstead:cook/cast_iron_stomach"));
        title = ProfessionTitles.resolve(cook, mastered::contains);
        assertNotNull(title);
        assertEquals("pitmaster", title.id(), "the larger completed build outranks the smaller");

        assertNull(ProfessionTitles.resolve(cook, skill -> false),
                "no completed build means the plain profession name");
    }

    @Test
    void pathTitleAcceptsOneChoiceFromEveryLevel() {
        ResourceLocation cook = id("townstead:cook");
        ResourceLocation pace = id("townstead:cook/chef/kitchen_pace");
        ResourceLocation knife = id("townstead:cook/chef/chefs_knife");
        ResourceLocation soup = id("townstead:cook/chef/soup_course");
        ResourceLocation precision = id("townstead:cook/chef/precision_cut");
        ProfessionTitles.replaceAll(Map.of(cook, List.of(
                new ProfessionTitles.Title(cook, "chef", Component.literal("Chef"), List.of(),
                        List.of(List.of(pace, knife), List.of(soup, precision))))));

        Set<ResourceLocation> complete = Set.of(knife, soup);
        assertEquals("chef", ProfessionTitles.resolve(cook, complete::contains).id());
        assertNull(ProfessionTitles.resolve(cook, Set.of(knife)::contains));
    }

    @Test
    void shippedComboSkillsParse() throws Exception {
        record Expected(String file, String career, int level) {}
        for (Expected e : new Expected[]{
                new Expected("charcutier", "minecraft:butcher", 2)}) {
            try (var in = ComboAndTitleTest.class.getResourceAsStream(
                    "/data/townstead/combo_skill/" + e.file() + ".json")) {
                assertNotNull(in, "shipped combo skill missing: " + e.file());
                ComboSkillDef def = ComboSkillDef.parse(id("townstead:" + e.file()),
                        JsonParser.parseReader(new java.io.InputStreamReader(
                                in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(),
                        Map.of(), new Diagnostics());
                assertNotNull(def, e.file() + " must parse");
                assertEquals(e.level(), def.thresholds().get(id(e.career())), e.file());
                assertTrue(def.thresholds().containsKey(id("townstead:cook")),
                        e.file() + ": the sample joins through Cook");
                assertFalse(def.grants().isEmpty(), e.file() + " must grant something real");
            }
        }
    }

    @Test
    void shippedTitlesReferenceRealSkillFiles() throws Exception {
        record Expected(String profession, String[] titleIds) {}
        for (Expected e : new Expected[]{
                new Expected("cook", new String[]{
                        "pizzaiolo"}),
                new Expected("scribe", new String[]{"chronicler"})}) {
            try (var in = ComboAndTitleTest.class.getResourceAsStream(
                    "/data/townstead/profession/" + e.profession() + "/profession.json")) {
                assertNotNull(in, "shipped def missing: " + e.profession());
                var def = JsonParser.parseReader(new java.io.InputStreamReader(
                        in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                if ("cook".equals(e.profession())) {
                    try (var pathIn = ComboAndTitleTest.class.getResourceAsStream(
                            "/data/townstead/profession/cook/path/pizzaiolo/path.json")) {
                        assertNotNull(pathIn);
                        ProfessionPathDocument.apply(def, "pizzaiolo", JsonParser.parseReader(
                                new java.io.InputStreamReader(pathIn,
                                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
                    }
                }
                assertTrue(def.has("titles"), e.profession() + " ships titles");
                var titles = def.getAsJsonArray("titles");
                assertEquals(e.titleIds().length, titles.size(), e.profession());
                for (var element : titles) {
                    var title = element.getAsJsonObject();
                    java.util.List<com.google.gson.JsonElement> requirements = new java.util.ArrayList<>();
                    if (title.has("skills")) requirements.addAll(title.getAsJsonArray("skills").asList());
                    if (title.has("skill_groups")) {
                        for (var group : title.getAsJsonArray("skill_groups")) {
                            requirements.addAll(group.getAsJsonArray().asList());
                        }
                    }
                    for (var skillRef : requirements) {
                        String raw = skillRef.getAsString();
                        int slash = raw.indexOf('/');
                        String skillFile = slash < 0
                                ? "/data/townstead/profession/" + e.profession()
                                        + "/skill/" + raw + ".json"
                                : "/data/townstead/profession/" + e.profession() + "/path/"
                                        + raw.substring(0, slash) + "/skill/"
                                        + raw.substring(slash + 1) + ".json";
                        assertNotNull(ComboAndTitleTest.class.getResource(skillFile),
                                title.get("id").getAsString() + " references missing skill "
                                        + skillRef.getAsString());
                    }
                }
            }
        }
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
