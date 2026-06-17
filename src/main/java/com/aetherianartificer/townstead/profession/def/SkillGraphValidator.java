package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.JsonPath;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates the loaded profession/skill graph for impossible or unreachable paths: dangling
 * profession or prerequisite references, tiers a profession can never reach, requires/excludes
 * contradictions, cross-profession prerequisites, and prerequisite cycles. Findings are emitted
 * as located {@link com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic}s, so they
 * surface in {@code /pheno validate} and at load time alongside gene diagnostics.
 */
public final class SkillGraphValidator {

    private SkillGraphValidator() {}

    public static void validate(Map<ResourceLocation, ProfessionDef> professions,
                                Map<ResourceLocation, SkillDef> skills, Diagnostics diag) {
        for (SkillDef skill : skills.values()) {
            diag.forResource(skill.id());

            ProfessionDef profession = professions.get(skill.profession());
            if (profession == null) {
                diag.error(JsonPath.ROOT.field("profession"),
                        "Unknown profession '" + skill.profession() + "'.",
                        "Define data/<ns>/profession/" + skill.profession().getPath() + ".json.");
            } else if (skill.tier() > profession.progression().maxTier()) {
                diag.warning(JsonPath.ROOT.field("tier"),
                        "Tier " + skill.tier() + " exceeds the profession's max tier "
                                + profession.progression().maxTier() + "; this skill can never unlock.",
                        "Lower the tier or add tiers to the profession.");
            }

            for (int i = 0; i < skill.requires().size(); i++) {
                ResourceLocation dep = skill.requires().get(i);
                SkillDef depSkill = skills.get(dep);
                if (depSkill == null) {
                    diag.error(JsonPath.ROOT.field("requires").index(i),
                            "Unknown prerequisite skill '" + dep + "'.",
                            "Check the id or define the prerequisite skill.");
                } else if (!depSkill.profession().equals(skill.profession())) {
                    diag.warning(JsonPath.ROOT.field("requires").index(i),
                            "Prerequisite '" + dep + "' belongs to a different profession.",
                            "Cross-profession prerequisites are rarely reachable; confirm this is intended.");
                }
                if (skill.exclusiveWith().contains(dep)) {
                    diag.error(JsonPath.ROOT.field("requires").index(i),
                            "Skill both requires and excludes '" + dep + "'; it can never be learned.",
                            "Remove one of the relations.");
                }
            }

            for (int i = 0; i < skill.exclusiveWith().size(); i++) {
                ResourceLocation other = skill.exclusiveWith().get(i);
                if (!skills.containsKey(other)) {
                    diag.warning(JsonPath.ROOT.field("exclusive_with").index(i),
                            "Unknown exclusivity reference '" + other + "'.",
                            "Check the id; the exclusivity has no effect otherwise.");
                }
            }
        }

        for (ResourceLocation inCycle : findCycles(skills)) {
            diag.forResource(inCycle);
            diag.error(JsonPath.ROOT.field("requires"),
                    "Prerequisite cycle: this skill is part of a requires loop and can never be learned.",
                    "Break the cycle in the requires graph.");
        }
    }

    /**
     * Skills that actually lie on a prerequisite cycle. DFS three-colouring over requires edges,
     * keeping the current path: a back-edge to a grey node marks only the path segment from that
     * grey node to the current node, so a skill that merely leads into a cycle (A -&gt; B -&gt; C
     * -&gt; B) is not falsely reported.
     */
    private static Set<ResourceLocation> findCycles(Map<ResourceLocation, SkillDef> skills) {
        Map<ResourceLocation, Integer> colour = new HashMap<>();   // 0 white, 1 grey, 2 black
        Set<ResourceLocation> inCycle = new HashSet<>();
        List<ResourceLocation> path = new ArrayList<>();
        for (ResourceLocation id : skills.keySet()) {
            if (colour.getOrDefault(id, 0) == 0) {
                visit(id, skills, colour, path, inCycle);
            }
        }
        return inCycle;
    }

    private static void visit(ResourceLocation id, Map<ResourceLocation, SkillDef> skills,
                              Map<ResourceLocation, Integer> colour, List<ResourceLocation> path,
                              Set<ResourceLocation> inCycle) {
        colour.put(id, 1);
        path.add(id);
        SkillDef skill = skills.get(id);
        List<ResourceLocation> deps = skill == null ? List.of() : skill.requires();
        for (ResourceLocation dep : deps) {
            if (!skills.containsKey(dep)) continue;
            int c = colour.getOrDefault(dep, 0);
            if (c == 1) {
                // Back-edge: dep is on the current path. Everything from dep to here is a cycle.
                int from = path.indexOf(dep);
                for (int k = from; k < path.size(); k++) {
                    inCycle.add(path.get(k));
                }
            } else if (c == 0) {
                visit(dep, skills, colour, path, inCycle);
            }
        }
        colour.put(id, 2);
        path.remove(path.size() - 1);
    }
}
