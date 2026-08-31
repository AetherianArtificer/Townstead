package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.ProgressionTrack;
import com.aetherianartificer.townstead.profession.def.RetrainingPolicy;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.profession.def.UnlockModel;
import com.aetherianartificer.townstead.villager.ProfessionXp;
import com.aetherianartificer.townstead.villager.ProfessionXpStore;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SkillPointsTest {

    private static final ResourceLocation COOK = id("test:cook");
    private static final ResourceLocation FARMER = id("test:farmer");
    private static final ResourceLocation BAKER = id("test:baker");
    private static final ResourceLocation COOK_SKILL = id("test:cook_skill");
    private static final ResourceLocation COOK_SIBLING = id("test:cook_sibling");
    private static final ResourceLocation COOK_CHILD = id("test:cook_child");
    private static final ResourceLocation BAKER_SKILL = id("test:baker_skill");

    private final CareerProfile profile = new CareerProfile();
    private final FakeStore store = new FakeStore();

    @BeforeEach
    void registerGraph() {
        Map<ResourceLocation, ProfessionDef> careers = new LinkedHashMap<>();
        careers.put(COOK, root(COOK, List.of(COOK_SKILL, COOK_SIBLING, COOK_CHILD)));
        careers.put(FARMER, root(FARMER, List.of()));
        careers.put(BAKER, gated(BAKER, List.of(BAKER_SKILL)));
        ProfessionDefs.replaceAll(careers);

        Map<ResourceLocation, SkillDef> skills = new LinkedHashMap<>();
        skills.put(COOK_SKILL, skill(COOK_SKILL, COOK, 1));
        skills.put(COOK_SIBLING, skill(COOK_SIBLING, COOK, 1));
        skills.put(COOK_CHILD, new SkillDef(COOK_CHILD, null, null, COOK, 2,
                List.of(COOK_SKILL), List.of(), 1, List.of(), null));
        skills.put(BAKER_SKILL, skill(BAKER_SKILL, BAKER, 2));
        SkillDefs.replaceAll(skills);
    }

    @AfterEach
    void clearGraph() {
        ProfessionDefs.replaceAll(Map.of());
        SkillDefs.replaceAll(Map.of());
    }

    @Test
    void insightCombinesEveryRegisteredCareerButNotUntouchedTierOneCareers() {
        profile.setPrimaryVocation(COOK);
        profile.acquireCareer(BAKER);
        store.setProfessionXp(COOK.toString(), new ProfessionXp(100, 0, 0, 0, 0));

        assertEquals(3, SkillPoints.earned(profile, store),
                "Cook rank 2 and Baker rank 1 contribute; untouched Farmer does not");
    }

    @Test
    void insightEarnedInOneCareerCanPayForAnother() {
        profile.setPrimaryVocation(COOK);
        profile.acquireCareer(BAKER);
        store.setProfessionXp(COOK.toString(), new ProfessionXp(100, 0, 0, 0, 0));

        assertEquals(1, SkillPoints.available(profile, store, Set.of(BAKER_SKILL)),
                "the two-Insight Baker skill spends from the combined three-Insight budget");
    }

    @Test
    void authoredParentsCreateHierarchyWhileMatchingRanksDoNot() {
        assertNull(SkillPoints.relationshipBlocker(Set.of(COOK_SKILL),
                        SkillDefs.byId(COOK_SIBLING)),
                "an unrelated skill at the same rank must not close this node");
        assertEquals("missing prerequisite 'test:cook_skill'",
                SkillPoints.relationshipBlocker(Set.of(COOK_SIBLING),
                        SkillDefs.byId(COOK_CHILD)));
        assertNull(SkillPoints.relationshipBlocker(Set.of(COOK_SKILL),
                        SkillDefs.byId(COOK_CHILD)),
                "the authored parent, not a fixed rank rule, opens the child");
    }

    private static ProfessionDef root(ResourceLocation id, List<ResourceLocation> skills) {
        return new ProfessionDef(id, null, null,
                new ProgressionTrack(List.of(0, 100, 200), 0), UnlockModel.EXPERIENTIAL,
                1, RetrainingPolicy.FREE, skills);
    }

    private static ProfessionDef gated(ResourceLocation id, List<ResourceLocation> skills) {
        return new ProfessionDef(id, null, null,
                new ProgressionTrack(List.of(0, 100, 200), 0), UnlockModel.EXPERIENTIAL,
                1, RetrainingPolicy.FREE, skills, false,
                com.aetherianartificer.townstead.pheno.condition.Conditions.ALWAYS,
                List.of("archive"), List.of());
    }

    private static SkillDef skill(ResourceLocation id, ResourceLocation career, int cost) {
        return new SkillDef(id, null, null, career, 1, List.of(), List.of(), cost, List.of(), null);
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.tryParse(value);
    }

    private static final class FakeStore implements ProfessionXpStore {
        private final Map<String, ProfessionXp> progress = new LinkedHashMap<>();

        @Override
        public ProfessionXp professionXp(String professionId) {
            return progress.getOrDefault(professionId, ProfessionXp.EMPTY);
        }

        @Override
        public void setProfessionXp(String professionId, ProfessionXp value) {
            progress.put(professionId, value);
        }
    }
}
