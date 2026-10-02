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

class ComboPowerTest {

    @BeforeAll static void registerModifier() {
        if (GeneTypes.get(ModifierGeneType.KEY).isEmpty()) GeneTypes.register(new ModifierGeneType());
    }

    @Test void aComboCarriesAPowerAndCompanions() {
        Diagnostics diag = new Diagnostics();
        ComboSkillDef combo = ComboSkillDef.parse(ResourceLocation.tryParse("t:c"), JsonParser.parseString("""
                {"professions":{"t:a":3,"t:b":3},
                 "power":{"type":"pheno:modifier","target":"map_fill_radius","operation":"multiply","value":2},
                 "companions":{"extra":{"type":"pheno:modifier","target":"food","operation":"multiply","value":1.25}}}
                """).getAsJsonObject(), Map.of(), diag);
        assertNotNull(combo, String.valueOf(diag.all()));
        assertInstanceOf(ModifierGeneType.Instance.class, combo.power());
        assertEquals(1, combo.companions().size());
    }

    @Test void aGrantOnlyComboStillParses() {
        ComboSkillDef combo = ComboSkillDef.parse(ResourceLocation.tryParse("t:c"), JsonParser.parseString("""
                {"professions":{"t:a":2,"t:b":2},
                 "grants":[{"capability":"t:x_flat","op":"add","value":1}]}
                """).getAsJsonObject(), Map.of(), new Diagnostics());
        assertNotNull(combo);
        assertNull(combo.power());
        assertTrue(combo.companions().isEmpty());
    }
}
