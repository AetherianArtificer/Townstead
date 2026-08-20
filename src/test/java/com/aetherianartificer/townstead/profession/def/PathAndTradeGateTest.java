package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Specialization paths: a branch inside a profession opened by buying its gateway skill,
 * plus the trade gate that keeps a path's wares hidden until the merchant specs in.
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
                id("townstead:cook/pizza_craft"),
                List.of(id("townstead:cook/kitchen_rhythm")),
                List.of(id("pizzadelight:basin")));
        ProfessionPaths.replaceAll(Map.of(COOK, List.of(path)));

        assertSame(path, ProfessionPaths.pathOwning(COOK, id("townstead:cook/pizza_craft")),
                "the gateway is a member");
        assertSame(path, ProfessionPaths.pathOwning(COOK, id("townstead:cook/kitchen_rhythm")));
        assertNull(ProfessionPaths.pathOwning(COOK, id("townstead:cook/open_flame")),
                "trunk skills belong to no path");

        // Any option on the path commits you to it: your first pick IS the path choice, so
        // there is no designated opening skill left for a member to be measured against.
        Set<ResourceLocation> viaMember = Set.of(id("townstead:cook/kitchen_rhythm"));
        assertEquals(List.of(path), ProfessionPaths.speccedPaths(viaMember::contains));
        Set<ResourceLocation> viaFirst = Set.of(id("townstead:cook/pizza_craft"));
        assertEquals(List.of(path), ProfessionPaths.speccedPaths(viaFirst::contains));
    }

    @Test
    void committedPathIsAnyOptionYouOwn() {
        ProfessionPaths.Path pizzaiolo = new ProfessionPaths.Path(COOK, "pizzaiolo",
                net.minecraft.network.chat.Component.literal("Pizzaiolo"),
                id("townstead:cook/pizza_craft"),
                List.of(id("townstead:cook/kitchen_rhythm")), List.of(id("pizzadelight:basin")));
        ProfessionPaths.Path rotisseur = new ProfessionPaths.Path(COOK, "rotisseur",
                net.minecraft.network.chat.Component.literal("Rotisseur"),
                id("townstead:cook/open_flame"),
                List.of(id("townstead:cook/turning_spit")), List.of(id("farmersdelight:skillet")));
        ProfessionPaths.replaceAll(Map.of(COOK, List.of(pizzaiolo, rotisseur)));

        assertNull(ProfessionPaths.committedPath(COOK, skill -> false),
                "owning nothing leaves the choice open");
        Set<ResourceLocation> first = Set.of(id("townstead:cook/pizza_craft"));
        assertSame(pizzaiolo, ProfessionPaths.committedPath(COOK, first::contains));
        Set<ResourceLocation> later = Set.of(id("townstead:cook/kitchen_rhythm"));
        assertSame(pizzaiolo, ProfessionPaths.committedPath(COOK, later::contains),
                "reached by any option, not only by a designated first one");
        Set<ResourceLocation> rival = Set.of(id("townstead:cook/turning_spit"));
        assertSame(rotisseur, ProfessionPaths.committedPath(COOK, rival::contains));
    }

    @Test
    void tradeSkillGateParsesAndScopes() {
        JsonObject gated = JsonParser.parseString("""
                {"cost": {"item": "minecraft:emerald", "count": 1},
                 "result": {"item": "pizzadelight:cheese", "count": 3},
                 "requires_skill": "pizza_craft"}""").getAsJsonObject();
        TradeDef trade = TradeDef.parse(gated, COOK);
        assertNotNull(trade);
        assertEquals(id("townstead:cook/pizza_craft"), trade.requiresSkill(),
                "bare refs scope to the owning profession's directory");

        gated.addProperty("requires_skill", "othermod:career/skill");
        assertEquals(id("othermod:career/skill"), TradeDef.parse(gated, COOK).requiresSkill());

        gated.remove("requires_skill");
        assertNull(TradeDef.parse(gated, COOK).requiresSkill(),
                "ungated trades stay unconditional");
    }

    @Test
    void shippedCookPathReferencesRealSkillFiles() throws Exception {
        try (var in = PathAndTradeGateTest.class.getResourceAsStream(
                "/data/townstead/profession/cook/profession.json")) {
            assertNotNull(in);
            JsonObject def = JsonParser.parseReader(new java.io.InputStreamReader(
                    in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(def.has("paths"), "cook ships its specialization paths");
            for (var element : def.getAsJsonArray("paths")) {
                JsonObject path = element.getAsJsonObject();
                String gateway = path.get("gateway").getAsString();
                assertSkillFileExists(gateway);
                Set<String> members = new java.util.HashSet<>(Set.of(gateway));
                for (var skill : path.getAsJsonArray("skills")) {
                    assertSkillFileExists(skill.getAsString());
                    members.add(skill.getAsString());
                }
                assertFalse(path.getAsJsonArray("worksites").isEmpty(),
                        "a path without worksites cannot steer villagers");
                // Under levels-and-options a skill's LEVEL is the only thing gating it, and a
                // prerequisite would silently make an option unreachable for anyone who answered
                // an earlier level differently, which is precisely the freedom the model exists
                // to give. So: no prerequisites on a path option, ever, and every option must sit
                // inside the track.
                java.util.Map<Integer, Integer> perLevel = new java.util.HashMap<>();
                for (String member : members) {
                    JsonObject skill = readSkill(member);
                    assertFalse(skill.has("requires"),
                            path.get("id").getAsString() + ": " + member
                                    + " must not declare prerequisites; its level is the gate");
                    int tier = skill.has("tier") ? skill.get("tier").getAsInt() : 1;
                    assertTrue(tier >= 1 && tier <= 5,
                            member + " sits at level " + tier + ", outside the five-level track");
                    perLevel.merge(tier, 1, Integer::sum);
                }
                // A level offering one option is not a choice, it is a handout.
                for (var count : perLevel.entrySet()) {
                    assertTrue(count.getValue() >= 2, path.get("id").getAsString()
                            + ": level " + count.getKey() + " offers only "
                            + count.getValue() + " option; a level must offer a real choice");
                }
            }
            // Every skill-gated trade must reference a real skill file too. Cook's progression
            // ships as a sidecar, so the trades live there.
            JsonObject levels;
            try (var levelsIn = PathAndTradeGateTest.class.getResourceAsStream(
                    "/data/townstead/profession/cook/levels.json")) {
                assertNotNull(levelsIn, "cook ships a levels.json sidecar");
                levels = JsonParser.parseReader(new java.io.InputStreamReader(
                        levelsIn, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            }
            for (var level : levels.getAsJsonArray("levels")) {
                if (!level.getAsJsonObject().has("trades")) continue;
                for (var t : level.getAsJsonObject().getAsJsonArray("trades")) {
                    JsonObject trade = t.getAsJsonObject();
                    if (trade.has("requires_skill")) {
                        assertSkillFileExists(trade.get("requires_skill").getAsString());
                    }
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
                        "/data/townstead/profession/cook/skill").toURI());
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
        }
        assertTrue(abilities > 0, "the scan must actually find the shipped abilities");
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

    private static JsonObject readSkill(String bareRef) throws Exception {
        try (var in = PathAndTradeGateTest.class.getResourceAsStream(
                "/data/townstead/profession/cook/skill/" + bareRef + ".json")) {
            assertNotNull(in, "referenced skill file missing: " + bareRef);
            return JsonParser.parseReader(new java.io.InputStreamReader(
                    in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void assertSkillFileExists(String bareRef) {
        String file = "/data/townstead/profession/cook/skill/" + bareRef + ".json";
        assertNotNull(PathAndTradeGateTest.class.getResource(file),
                "referenced skill file missing: " + bareRef);
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
