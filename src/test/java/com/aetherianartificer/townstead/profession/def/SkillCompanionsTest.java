package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.aetherianartificer.townstead.root.gene.GeneTypes;
import com.aetherianartificer.townstead.root.gene.types.ModifierGeneType;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkillCompanionsTest {

    @BeforeAll static void registerModifier() {
        // Register only if absent: a duplicate logs through Townstead.LOGGER, which cannot
        // initialize outside mod loading.
        if (GeneTypes.get(ModifierGeneType.KEY).isEmpty()) GeneTypes.register(new ModifierGeneType());
    }

    @Test void companionsRideTheSkill() {
        Diagnostics diag = new Diagnostics();
        diag.forResource(ResourceLocation.tryParse("t:s"));
        SkillDef skill = ProfessionDataLoader.parseSkill(ResourceLocation.tryParse("t:s"),
                JsonParser.parseString("""
                        {"profession":"t:p","tier":1,
                         "power":{"type":"pheno:modifier","target":"fishing_lure","operation":"add","value":1},
                         "companions":{"luck":{"type":"pheno:modifier","target":"fishing_luck","operation":"add","value":1}}}
                        """).getAsJsonObject(), Map.of(), diag);
        assertNotNull(skill);
        assertNotNull(skill.power(), String.valueOf(diag.all()));
        assertEquals(1, skill.companions().size(), String.valueOf(diag.all()));
        assertTrue(skill.companions().get("luck") instanceof ModifierGeneType.Instance);
    }

    @Test void aBadCompanionDropsAlone() {
        Diagnostics diag = new Diagnostics();
        diag.forResource(ResourceLocation.tryParse("t:s"));
        SkillDef skill = ProfessionDataLoader.parseSkill(ResourceLocation.tryParse("t:s"),
                JsonParser.parseString("""
                        {"profession":"t:p","tier":1,
                         "companions":{"good":{"type":"pheno:modifier","target":"fishing_luck","value":1},
                                       "bad":{"type":"pheno:unknown"}}}
                        """).getAsJsonObject(), Map.of(), diag);
        assertNotNull(skill);
        assertEquals(1, skill.companions().size());
        assertTrue(skill.companions().containsKey("good"));
    }
}
