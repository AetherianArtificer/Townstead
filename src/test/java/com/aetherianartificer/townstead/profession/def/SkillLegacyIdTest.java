package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Skills that moved into per-profession directories gained path-scoped ids; learned history
 * saved under the old flat ids must keep resolving through the legacy index, but only when the
 * flat form is unambiguous.
 */
class SkillLegacyIdTest {

    @AfterEach
    void reset() {
        SkillDefs.replaceAll(Map.of());
    }

    @Test
    void flatIdResolvesToPathScopedSuccessor() {
        SkillDefs.replaceAll(registry("townstead:cook/open_flame"));
        assertNotNull(SkillDefs.byId(id("townstead:open_flame")));
        assertEquals(id("townstead:cook/open_flame"),
                SkillDefs.canonicalId(id("townstead:open_flame")));
        assertEquals(id("townstead:cook/open_flame"),
                SkillDefs.canonicalId(id("townstead:cook/open_flame")),
                "registered ids canonicalize to themselves");
    }

    @Test
    void ambiguousFlatIdStaysUnresolved() {
        SkillDefs.replaceAll(registry("townstead:cook/quick_hands", "townstead:scribe/quick_hands"));
        assertNull(SkillDefs.byId(id("townstead:quick_hands")),
                "two candidates means no safe remap");
        assertEquals(id("townstead:quick_hands"), SkillDefs.canonicalId(id("townstead:quick_hands")));
    }

    private static Map<ResourceLocation, SkillDef> registry(String... ids) {
        Map<ResourceLocation, SkillDef> out = new LinkedHashMap<>();
        for (String raw : ids) {
            ResourceLocation skillId = id(raw);
            Diagnostics diagnostics = new Diagnostics();
            diagnostics.forResource(skillId);
            SkillDef def = ProfessionDataLoader.parseSkill(skillId,
                    JsonParser.parseString("""
                            {"profession": "test:subject", "tier": 1}""").getAsJsonObject(),
                    Map.of(), diagnostics);
            assertNotNull(def);
            out.put(skillId, def);
        }
        return out;
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
