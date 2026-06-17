package com.aetherianartificer.townstead.profession.skill;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.ProgressionTrack;
import com.aetherianartificer.townstead.profession.def.RetrainingPolicy;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.profession.def.UnlockModel;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearnedSkillsTest {

    private static final UUID ENTITY = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static ResourceLocation rl(String s) {
        return ResourceLocation.tryParse(s);
    }

    private static ProfessionDef profession(String id, RetrainingPolicy retraining) {
        return new ProfessionDef(rl(id), null, null, new ProgressionTrack(List.of(0, 100, 200), 0),
                UnlockModel.EXPERIENTIAL, 1, retraining, List.of());
    }

    private static SkillDef skill(String id, String profession, List<ResourceLocation> requires,
                                  List<ResourceLocation> exclusiveWith) {
        return new SkillDef(rl(id), null, null, rl(profession), 1, requires, exclusiveWith, 1, List.of(), null);
    }

    @BeforeAll
    static void seedGraph() {
        Map<ResourceLocation, ProfessionDef> professions = new LinkedHashMap<>();
        professions.put(rl("t:free"), profession("t:free", RetrainingPolicy.FREE));
        professions.put(rl("t:locked"), profession("t:locked", RetrainingPolicy.LOCKED));
        professions.put(rl("t:costly"), profession("t:costly", RetrainingPolicy.COSTLY));
        ProfessionDefs.replaceAll(professions);

        Map<ResourceLocation, SkillDef> skills = new LinkedHashMap<>();
        skills.put(rl("t:base"), skill("t:base", "t:free", List.of(), List.of()));
        skills.put(rl("t:adv"), skill("t:adv", "t:free", List.of(rl("t:base")), List.of()));
        skills.put(rl("t:ex_a"), skill("t:ex_a", "t:free", List.of(), List.of(rl("t:ex_b"))));
        skills.put(rl("t:ex_b"), skill("t:ex_b", "t:free", List.of(), List.of()));
        skills.put(rl("t:locked_skill"), skill("t:locked_skill", "t:locked", List.of(), List.of()));
        skills.put(rl("t:costly_skill"), skill("t:costly_skill", "t:costly", List.of(), List.of()));
        SkillDefs.replaceAll(skills);
    }

    @BeforeEach
    void reset() {
        LearnedSkills.clear(ENTITY);
    }

    @Test
    void learnRejectsMissingPrerequisite() {
        assertFalse(LearnedSkills.learn(ENTITY, rl("t:adv")).ok(), "adv requires base");
        assertTrue(LearnedSkills.learn(ENTITY, rl("t:base")).ok());
        assertTrue(LearnedSkills.learn(ENTITY, rl("t:adv")).ok(), "adv learnable once base is held");
    }

    @Test
    void learnRejectsExclusiveSibling() {
        assertTrue(LearnedSkills.learn(ENTITY, rl("t:ex_a")).ok());
        assertFalse(LearnedSkills.learn(ENTITY, rl("t:ex_b")).ok(), "ex_b is exclusive with ex_a");
    }

    @Test
    void forgetCascadesToDependents() {
        LearnedSkills.learn(ENTITY, rl("t:base"));
        LearnedSkills.learn(ENTITY, rl("t:adv"));
        LearnedSkills.ForgetResult result = LearnedSkills.forget(ENTITY, rl("t:base"));
        assertTrue(result.ok());
        assertTrue(result.removed().contains(rl("t:base")));
        assertTrue(result.removed().contains(rl("t:adv")), "forgetting base must cascade to adv");
        assertTrue(LearnedSkills.learned(ENTITY).isEmpty());
    }

    @Test
    void lockedRetrainingRejectsForget() {
        LearnedSkills.forceLearn(ENTITY, rl("t:locked_skill"));
        assertFalse(LearnedSkills.forget(ENTITY, rl("t:locked_skill")).ok(), "locked profession forbids forgetting");
        assertTrue(LearnedSkills.has(ENTITY, rl("t:locked_skill")));
    }

    @Test
    void costlyRetrainingIsNotYetFunctionalButForceWorks() {
        LearnedSkills.forceLearn(ENTITY, rl("t:costly_skill"));
        LearnedSkills.ForgetResult denied = LearnedSkills.forget(ENTITY, rl("t:costly_skill"));
        assertFalse(denied.ok(), "costly retraining is rejected until its payment mechanism exists");
        assertTrue(denied.error() != null && denied.error().contains("not available"));
        assertTrue(LearnedSkills.forceForget(ENTITY, rl("t:costly_skill")).ok(), "force bypasses the policy");
    }
}
