package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The built-in career defs replaced the hardcoded {@code ProfessionXpType} enum; these
 * numbers are the save-compatibility contract, so the shipped JSON must reproduce them
 * exactly. Parses the real resources from the jar/classpath, not copies.
 */
class BuiltinProfessionParityTest {

    @Test
    void farmerMatchesLegacyNumbers() {
        assertProgression("/data/minecraft/profession/farmer/profession.json", "minecraft:farmer",
                List.of(0, 120, 320, 700, 1300), 240, 1300);
    }

    @Test
    void butcherMatchesLegacyNumbers() {
        assertProgression("/data/minecraft/profession/butcher/profession.json", "minecraft:butcher",
                List.of(0, 20, 60, 120, 200), 60, 200);
    }

    @Test
    void shepherdMatchesLegacyNumbers() {
        assertProgression("/data/minecraft/profession/shepherd/profession.json", "minecraft:shepherd",
                List.of(0, 20, 60, 120, 200), 60, 200);
    }

    @Test
    void cookMatchesLegacyNumbers() {
        ProfessionDef cook = load("/data/townstead/profession/cook/profession.json", "townstead:cook");
        assertEquals(List.of(0, 110, 300, 660, 1250),
                cook.progression().tierThresholds().subList(0, 5), "cook tiers 1-5");
        assertEquals(230, cook.progression().dailyCap(), "cook daily cap");
        assertEquals(1250, cook.progression().maxXp(), "cook max xp");
    }

    private static void assertProgression(String resource, String id,
                                          List<Integer> tiers, int dailyCap, int maxXp) {
        ProfessionDef def = load(resource, id);
        assertEquals(tiers, def.progression().tierThresholds(), resource + " tiers");
        assertEquals(dailyCap, def.progression().dailyCap(), resource + " daily cap");
        assertEquals(maxXp, def.progression().maxXp(), resource + " max xp");
    }

    private static ProfessionDef load(String resource, String id) {
        InputStream in = BuiltinProfessionParityTest.class.getResourceAsStream(resource);
        assertNotNull(in, "shipped resource missing: " + resource);
        JsonObject json = JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        InputStream sidecar = BuiltinProfessionParityTest.class.getResourceAsStream(
                resource.substring(0, resource.lastIndexOf('/') + 1) + "progression.json");
        assertNotNull(sidecar, "shipped progression missing: " + resource);
        ProfessionProgressionOverlay.apply(json, JsonParser.parseReader(
                new InputStreamReader(sidecar, StandardCharsets.UTF_8)).getAsJsonObject());
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.forResource(ResourceLocation.tryParse(id));
        ProfessionDef def = ProfessionDataLoader.parseProfession(
                ResourceLocation.tryParse(id), json, Map.of(), diagnostics);
        assertNotNull(def, resource + " must parse");
        return def;
    }
}
