package com.aetherianartificer.townstead.profession.career;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CareerProfileTest {
    @Test
    void switchingPrimaryRetainsHistory() {
        CareerProfile profile = new CareerProfile();
        ResourceLocation cook = id("townstead:cook");
        ResourceLocation farmer = id("minecraft:farmer");
        profile.setPrimaryVocation(cook);
        profile.setPrimaryVocation(farmer);
        assertEquals(farmer, profile.primaryVocation());
        assertTrue(profile.careerHistory().containsAll(List.of(cook, farmer)));
    }

    @Test
    void learnedSkillsRemainWhileOnePerSkillGroupIsEquipped() {
        CareerProfile profile = new CareerProfile();
        ResourceLocation group = id("townstead:kitchen_style");
        ResourceLocation miseEnPlace = id("townstead:mise_en_place");
        ResourceLocation improvisation = id("townstead:improvisation");
        profile.learnChoice(miseEnPlace);
        profile.learnChoice(improvisation);
        assertTrue(profile.activateSkill(group, miseEnPlace));
        assertTrue(profile.activateSkill(group, improvisation));
        assertEquals(improvisation, profile.activeBySkillGroup().get(group));
        assertTrue(profile.learnedChoices().containsAll(List.of(miseEnPlace, improvisation)));
    }

    @Test
    void loadoutIsUniqueAndBounded() {
        CareerProfile profile = new CareerProfile();
        ResourceLocation a = id("townstead:a");
        ResourceLocation b = id("townstead:b");
        ResourceLocation c = id("townstead:c");
        profile.setActiveLoadout(List.of(a, a, b, c), 2);
        assertEquals(List.of(a, b), profile.activeLoadout());
    }

    @Test
    void legacyBareXpKeyReadsThroughCanonicalIdAndMigratesOnWrite() {
        CareerProfile profile = new CareerProfile();
        profile.setProfessionXp("cook", new com.aetherianartificer.townstead.villager.ProfessionXp(150, 2, 0L, 0L, 0));
        assertEquals(150, profile.professionXp("townstead:cook").xp(), "canonical read falls back to bare key");

        profile.setProfessionXp("townstead:cook",
                new com.aetherianartificer.townstead.villager.ProfessionXp(200, 2, 0L, 0L, 0));
        assertEquals(200, profile.professionXp("townstead:cook").xp());
        assertEquals(0, profile.professionXp("cook").xp(), "bare key retired on canonical write");
    }

    private static ResourceLocation id(String value) {
        //? if >=1.21 {
        return ResourceLocation.parse(value);
        //?} else {
        /*return new ResourceLocation(value);
        *///?}
    }
}
