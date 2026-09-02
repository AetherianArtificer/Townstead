package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Specialization paths: groupings inside a profession whose hierarchy comes from authored
 * parent relations, plus the trade gate that keeps a path's wares hidden until investment.
 */
class PathAndTradeGateTest {

    private static final ResourceLocation COOK = id("townstead:cook");

    @AfterEach
    void reset() {
        ProfessionPaths.replaceAll(Map.of());
    }

    @Test
    void pathMembershipAndSpecDetection() {
        ProfessionPaths.Path path = new ProfessionPaths.Path(COOK, "pizzaiolo",
                net.minecraft.network.chat.Component.literal("Pizzaiolo"),
                id("townstead:cook/pizzaiolo/pizza_craft"),
                List.of(id("townstead:cook/pizzaiolo/kitchen_rhythm")),
                List.of(id("pizzadelight:basin")));
        ProfessionPaths.replaceAll(Map.of(COOK, List.of(path)));

        assertSame(path, ProfessionPaths.pathOwning(COOK,
                id("townstead:cook/pizzaiolo/pizza_craft")),
                "the gateway is a member");
        assertSame(path, ProfessionPaths.pathOwning(COOK,
                id("townstead:cook/pizzaiolo/kitchen_rhythm")));
        assertNull(ProfessionPaths.pathOwning(COOK, id("townstead:cook/open_flame")),
                "trunk skills belong to no path");

        // Any member marks the path as invested; a normal purchase still reaches later members
        // through their authored parents, while this remains robust to imported/legacy saves.
        Set<ResourceLocation> viaMember = Set.of(
                id("townstead:cook/pizzaiolo/kitchen_rhythm"));
        assertEquals(List.of(path), ProfessionPaths.speccedPaths(viaMember::contains));
        Set<ResourceLocation> viaFirst = Set.of(id("townstead:cook/pizzaiolo/pizza_craft"));
        assertEquals(List.of(path), ProfessionPaths.speccedPaths(viaFirst::contains));
    }

    @Test
    void committedPathIsAnyOptionYouOwn() {
        ProfessionPaths.Path pizzaiolo = new ProfessionPaths.Path(COOK, "pizzaiolo",
                net.minecraft.network.chat.Component.literal("Pizzaiolo"),
                id("townstead:cook/pizzaiolo/pizza_craft"),
                List.of(id("townstead:cook/pizzaiolo/kitchen_rhythm")),
                List.of(id("pizzadelight:basin")));
        ProfessionPaths.Path rotisseur = new ProfessionPaths.Path(COOK, "rotisseur",
                net.minecraft.network.chat.Component.literal("Rotisseur"),
                id("townstead:cook/open_flame"),
                List.of(id("townstead:cook/turning_spit")), List.of(id("farmersdelight:skillet")));
        ProfessionPaths.replaceAll(Map.of(COOK, List.of(pizzaiolo, rotisseur)));

        assertNull(ProfessionPaths.committedPath(COOK, skill -> false),
                "owning nothing leaves the choice open");
        Set<ResourceLocation> first = Set.of(id("townstead:cook/pizzaiolo/pizza_craft"));
        assertSame(pizzaiolo, ProfessionPaths.committedPath(COOK, first::contains));
        Set<ResourceLocation> later = Set.of(
                id("townstead:cook/pizzaiolo/kitchen_rhythm"));
        assertSame(pizzaiolo, ProfessionPaths.committedPath(COOK, later::contains),
                "reached by any option, not only by a designated first one");
        Set<ResourceLocation> rival = Set.of(id("townstead:cook/turning_spit"));
        assertSame(rotisseur, ProfessionPaths.committedPath(COOK, rival::contains));
    }

    @Test
    void tradeSkillGateParsesAndScopes() {
        JsonObject gated = JsonParser.parseString("""
                {"cost":"minecraft:emerald","result":"pizzadelight:cheese",
                 "result_count":3,"requires":"pizza_craft"}""").getAsJsonObject();
        TradeDef trade = TradeDef.parse(gated, COOK, 2);
        assertNotNull(trade);
        assertEquals(3, trade.resultCount());
        assertEquals(12, trade.maxUses());
        assertEquals(5, trade.villagerXp());
        assertNull(trade.path());
        assertNotSame(com.aetherianartificer.townstead.pheno.condition.Conditions.ALWAYS,
                trade.requirements(), "a short Skill requirement becomes an executable gate");
        assertEquals(id("townstead:cook/pizzaiolo/pizza_craft"),
                TradeDef.skillRef("pizza_craft", COOK, "pizzaiolo"));
        assertEquals(id("townstead:cook/seasoned_hands"),
                TradeDef.skillRef("seasoned_hands", COOK, null));

        gated.remove("requires");
        gated.addProperty("path", "pizzaiolo");
        assertEquals("pizzaiolo", TradeDef.parse(gated, COOK, 2).path());

        gated.add("cost", JsonParser.parseString("{\"item\":\"minecraft:emerald\"}"));
        assertNull(TradeDef.parse(gated, COOK, 2), "the unpublished nested stack shape is rejected");
    }

    @Test
    void shippedCookPathReferencesRealSkillFiles() throws Exception {
        try (var in = PathAndTradeGateTest.class.getResourceAsStream(
                "/data/townstead/profession/cook/path/pizzaiolo/path.json")) {
            assertNotNull(in);
            JsonObject path = JsonParser.parseReader(new java.io.InputStreamReader(
                    in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            String pathId = "pizzaiolo";
            assertEquals(Boolean.TRUE,
                    com.aetherianartificer.townstead.data.ModGate.evaluate(path.get("mods"),
                            Set.of("pizzadelight", "farmersdelight")::contains),
                    "the complete Pizzaiolo tree loads when both providers are present");
            assertEquals(Boolean.FALSE,
                    com.aetherianartificer.townstead.data.ModGate.evaluate(path.get("mods"),
                            Set.of("farmersdelight")::contains),
                    "the whole Pizzaiolo tree stays hidden when Pizza Delight is absent");
            int pathLevel = 0;
            List<String> previousLane = List.of();
            for (var authoredLevel : path.getAsJsonArray("skills")) {
                pathLevel++;
                var members = authoredLevel.isJsonArray()
                        ? authoredLevel.getAsJsonArray()
                        : JsonParser.parseString("[" + authoredLevel + "]").getAsJsonArray();
                assertTrue(members.size() >= 2, pathId + ": level " + pathLevel
                        + " must offer a real choice");
                List<String> currentLane = new ArrayList<>();
                for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
                    String skillId = members.get(memberIndex).getAsString();
                    currentLane.add(skillId);
                    JsonObject skill = readSkill(pathId, skillId);
                    assertFalse(skill.has("mods"),
                            skillId + ": the Path owns its provider gate so members cannot split");
                    assertFalse(skill.getAsJsonObject("display_name").toString()
                                    .contains("PLACEHOLDER"),
                            skillId + ": must ship player-facing copy");
                    assertNotEquals("minecraft:barrier", skill.get("icon").getAsString(),
                            skillId + ": must ship a readable icon");
                    assertFalse(skill.getAsJsonObject("description").toString()
                                    .contains("PLACEHOLDER"),
                            skillId + ": must ship a player-facing description");
                    if (pathLevel == 1) {
                        assertFalse(skill.has("requires"),
                                skillId + ": a lane root must have no parent");
                    } else {
                        assertTrue(skill.has("requires"),
                                skillId + ": hierarchy must be authored as a parent relation");
                        assertEquals(List.of(previousLane.get(memberIndex)),
                                skill.getAsJsonArray("requires").asList().stream()
                                        .map(element -> element.getAsString()).toList(),
                                skillId + ": must continue its authored lane");
                    }
                    assertFalse(skill.has("tier"),
                            skillId + ": path position still owns its rank gate and board band");
                }
                previousLane = List.copyOf(currentLane);
            }
            JsonObject work;
            try (var workIn = PathAndTradeGateTest.class.getResourceAsStream(
                    "/data/townstead/profession/cook/work.json")) {
                assertNotNull(workIn);
                work = JsonParser.parseReader(new java.io.InputStreamReader(
                        workIn, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            }
            assertFalse(work.getAsJsonObject("path_worksites").getAsJsonArray(pathId).isEmpty(),
                    "a Path without worksites cannot steer villagers");
            // Every skill-gated offer in the independent trade sidecar references a real Skill.
            JsonObject trades;
            try (var tradesIn = PathAndTradeGateTest.class.getResourceAsStream(
                    "/data/townstead/profession/cook/path/pizzaiolo/trade/base.json")) {
                assertNotNull(tradesIn, "cook ships a Path trade contribution");
                trades = JsonParser.parseReader(new java.io.InputStreamReader(
                        tradesIn, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            }
            for (var level : trades.entrySet()) {
                if (Set.of("schema", "replace").contains(level.getKey())) continue;
                for (var t : level.getValue().getAsJsonArray()) {
                    JsonObject trade = t.getAsJsonObject();
                    if (trade.has("requires") && trade.get("requires").isJsonPrimitive()) {
                        assertSkillFileExists(pathId, trade.get("requires").getAsString());
                    }
                }
            }
            assertFalse(trades.has("path"), "the enclosing directory owns the Path link");
            JsonObject composed = JsonParser.parseString(
                    "{\"paths\":[{\"id\":\"pizzaiolo\"}]}").getAsJsonObject();
            ProfessionTradeDocument.apply(composed, trades, pathId);
            for (var authoredLevel : composed.getAsJsonObject("trades").entrySet()) {
                for (var offer : authoredLevel.getValue().getAsJsonArray()) {
                    assertEquals(pathId, offer.getAsJsonObject().get("path").getAsString());
                }
            }
        }
    }

    /**
     * Villagers fire their own abilities, so an area-of-effect that can damage and carries no
     * friendly-fire filter would have specced cooks hurting their neighbours during a raid. The
     * mistake is invisible in the data and would read as an AI bug in play, so it fails here
     * instead: any {@code area_of_effect} whose subtree can damage must name who it may hit.
     */
    @Test
    void shippedAbilitiesCannotHitFriendliesByAccident() throws Exception {
        java.io.File dir = new java.io.File(
                PathAndTradeGateTest.class.getResource(
                        "/data/townstead/profession/cook/path/pizzaiolo/skill").toURI());
        int abilities = 0;
        for (java.io.File file : java.util.Objects.requireNonNull(dir.listFiles())) {
            if (!file.getName().endsWith(".json")) continue;
            JsonObject skill = JsonParser.parseReader(new java.io.FileReader(
                    file, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if (!skill.has("power")) continue;
            JsonObject power = skill.getAsJsonObject("power");
            if (!"pheno:active_ability".equals(power.get("type").getAsString())) continue;
            abilities++;
            assertTrue(power.has("cooldown"), file.getName() + ": an active ability needs a cooldown");
            assertUnfilteredHarm(file.getName(), power.get("action"));
            assertSupportedDamageSources(file.getName(), power.get("action"));
        }
        assertTrue(abilities > 0, "the scan must actually find the shipped abilities");
    }

    /** Keeps authored damage sources inside the vocabulary accepted by DamageActionType. */
    private static void assertSupportedDamageSources(String file,
                                                     com.google.gson.JsonElement element) {
        if (element == null) return;
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) assertSupportedDamageSources(file, child);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject node = element.getAsJsonObject();
        if ("pheno:damage".equals(node.has("type") ? node.get("type").getAsString() : "")) {
            String source = node.has("source") ? node.get("source").getAsString() : "generic";
            assertTrue(Set.of("generic", "other").contains(source),
                    file + ": unsupported pheno:damage source '" + source + "'");
        }
        for (var entry : node.entrySet()) assertSupportedDamageSources(file, entry.getValue());
    }

    /** Walks an action tree, failing on any damaging area_of_effect with no bientity filter. */
    private static void assertUnfilteredHarm(String file, com.google.gson.JsonElement element) {
        if (element == null) return;
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) assertUnfilteredHarm(file, child);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject node = element.getAsJsonObject();
        String type = node.has("type") ? node.get("type").getAsString() : "";
        if ("pheno:area_of_effect".equals(type) && canHarm(node.get("action"))) {
            assertTrue(node.has("bientity_condition"),
                    file + ": a damaging area_of_effect must declare who it may hit");
        }
        for (var entry : node.entrySet()) assertUnfilteredHarm(file, entry.getValue());
    }

    private static boolean canHarm(com.google.gson.JsonElement element) {
        if (element == null) return false;
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) {
                if (canHarm(child)) return true;
            }
            return false;
        }
        if (!element.isJsonObject()) return false;
        JsonObject node = element.getAsJsonObject();
        String type = node.has("type") ? node.get("type").getAsString() : "";
        if ("pheno:damage".equals(type) || "pheno:ignite".equals(type)) return true;
        for (var entry : node.entrySet()) {
            if (canHarm(entry.getValue())) return true;
        }
        return false;
    }

    private static JsonObject readSkill(String path, String bareRef) throws Exception {
        try (var in = PathAndTradeGateTest.class.getResourceAsStream(
                "/data/townstead/profession/cook/path/" + path + "/skill/"
                        + bareRef + ".json")) {
            assertNotNull(in, "referenced skill file missing: " + bareRef);
            return JsonParser.parseReader(new java.io.InputStreamReader(
                    in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void assertSkillFileExists(String path, String bareRef) {
        String file = "/data/townstead/profession/cook/path/" + path + "/skill/"
                + bareRef + ".json";
        assertNotNull(PathAndTradeGateTest.class.getResource(file),
                "referenced skill file missing: " + bareRef);
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
