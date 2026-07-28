package com.aetherianartificer.townstead.profession.career;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
        // The same ability claimed twice: the earlier slot keeps it. Slot 3 is past the maximum.
        profile.setActiveLoadout(Map.of(1, a, 2, a, 3, b), 2);
        assertEquals(Map.of(1, a), profile.activeLoadout());
    }

    @Test
    void loadoutKeepsEmptySlotsSoNothingShifts() {
        CareerProfile profile = new CareerProfile();
        ResourceLocation a = id("townstead:a");
        ResourceLocation b = id("townstead:b");
        profile.setActiveLoadout(Map.of(1, a, 5, b), 8);
        assertEquals(Map.of(1, a, 5, b), profile.activeLoadout(), "a gap is a real arrangement");

        // Clearing slot 1 must not slide slot 5 down onto a key the player never chose for it.
        profile.setActiveLoadout(Map.of(5, b), 8);
        assertEquals(Map.of(5, b), profile.activeLoadout());
    }

    // NO NBT ROUND-TRIP TEST, though the loadout's storage shape changed and one would be worth
    // having. CompoundTag.put(String, Tag) resolves at compile time but is absent from the test
    // runtime classpath, so toTag() throws NoSuchMethodError before it reaches anything of ours.
    // Nothing in this suite had ever called toTag(), which is why the gap went unnoticed. Fixing it
    // is a build-classpath job, not a career one; until then the save shape is only covered in game.

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
