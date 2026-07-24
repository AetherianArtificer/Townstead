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
                id("townstead:cook/pizza_craft"),
                List.of(id("townstead:cook/kitchen_rhythm")),
                List.of(id("pizzadelight:basin")));
        ProfessionPaths.replaceAll(Map.of(COOK, List.of(path)));

        assertSame(path, ProfessionPaths.pathOwning(COOK, id("townstead:cook/pizza_craft")),
                "the gateway is a member");
        assertSame(path, ProfessionPaths.pathOwning(COOK, id("townstead:cook/kitchen_rhythm")));
        assertNull(ProfessionPaths.pathOwning(COOK, id("townstead:cook/open_flame")),
                "trunk skills belong to no path");

        Set<ResourceLocation> unspecced = Set.of(id("townstead:cook/kitchen_rhythm"));
        assertTrue(ProfessionPaths.speccedPaths(unspecced::contains).isEmpty(),
                "member skills without the gateway do not spec the entity");
        Set<ResourceLocation> specced = Set.of(id("townstead:cook/pizza_craft"));
        assertEquals(List.of(path), ProfessionPaths.speccedPaths(specced::contains));
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
            assertTrue(def.has("paths"), "cook ships the pizzaiolo path");
            for (var element : def.getAsJsonArray("paths")) {
                JsonObject path = element.getAsJsonObject();
                assertSkillFileExists(path.get("gateway").getAsString());
                for (var skill : path.getAsJsonArray("skills")) {
                    assertSkillFileExists(skill.getAsString());
                }
                assertFalse(path.getAsJsonArray("worksites").isEmpty(),
                        "a path without worksites cannot steer villagers");
            }
            // Every skill-gated trade must reference a real skill file too.
            for (var level : def.getAsJsonArray("levels")) {
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

    private static void assertSkillFileExists(String bareRef) {
        String file = "/data/townstead/profession/cook/skill/" + bareRef + ".json";
        assertNotNull(PathAndTradeGateTest.class.getResource(file),
                "referenced skill file missing: " + bareRef);
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
