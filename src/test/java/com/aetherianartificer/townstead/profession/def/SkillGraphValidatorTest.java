package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillGraphValidatorTest {

    private static ResourceLocation rl(String s) {
        return ResourceLocation.tryParse(s);
    }

    private static SkillDef skill(String id, String profession, int tier, List<ResourceLocation> requires) {
        return new SkillDef(rl(id), null, null, rl(profession), tier, requires, List.of(), 1, List.of(), null);
    }

    private static ProfessionDef profession(String id, int maxTier) {
        List<Integer> tiers = new ArrayList<>();
        for (int i = 0; i < maxTier; i++) tiers.add(i * 100);
        return new ProfessionDef(rl(id), null, null, new ProgressionTrack(tiers, 0),
                UnlockModel.EXPERIENTIAL, 1, RetrainingPolicy.FREE, List.of());
    }

    private static boolean cycleFlagged(Diagnostics diag, String resource) {
        for (Diagnostic d : diag.all()) {
            if (d.resource().toString().equals(resource) && d.message().toLowerCase().contains("cycle")) return true;
        }
        return false;
    }

    private static boolean anyMessageContains(Diagnostics diag, String fragment) {
        for (Diagnostic d : diag.all()) {
            if (d.message().contains(fragment)) return true;
        }
        return false;
    }

    @Test
    void cycleMarksOnlyTheCycleSegmentNotUpstream() {
        Map<ResourceLocation, ProfessionDef> professions = Map.of(rl("t:p"), profession("t:p", 9));
        Map<ResourceLocation, SkillDef> skills = new LinkedHashMap<>();
        skills.put(rl("t:a"), skill("t:a", "t:p", 1, List.of(rl("t:b"))));
        skills.put(rl("t:b"), skill("t:b", "t:p", 1, List.of(rl("t:c"))));
        skills.put(rl("t:c"), skill("t:c", "t:p", 1, List.of(rl("t:b"))));
        Diagnostics diag = new Diagnostics();
        SkillGraphValidator.validate(professions, skills, diag);
        assertTrue(cycleFlagged(diag, "t:b"), "b is on the cycle");
        assertTrue(cycleFlagged(diag, "t:c"), "c is on the cycle");
        assertFalse(cycleFlagged(diag, "t:a"), "a only leads into the cycle and must not be flagged");
    }

    @Test
    void danglingPrerequisiteIsReported() {
        Map<ResourceLocation, ProfessionDef> professions = Map.of(rl("t:p"), profession("t:p", 9));
        Map<ResourceLocation, SkillDef> skills = new LinkedHashMap<>();
        skills.put(rl("t:a"), skill("t:a", "t:p", 1, List.of(rl("t:missing"))));
        Diagnostics diag = new Diagnostics();
        SkillGraphValidator.validate(professions, skills, diag);
        assertTrue(anyMessageContains(diag, "Unknown prerequisite"));
    }

    @Test
    void tierBeyondProfessionMaxIsReported() {
        Map<ResourceLocation, ProfessionDef> professions = Map.of(rl("t:p"), profession("t:p", 3));
        Map<ResourceLocation, SkillDef> skills = new LinkedHashMap<>();
        skills.put(rl("t:a"), skill("t:a", "t:p", 9, List.of()));
        Diagnostics diag = new Diagnostics();
        SkillGraphValidator.validate(professions, skills, diag);
        assertTrue(anyMessageContains(diag, "exceeds"));
    }
}
