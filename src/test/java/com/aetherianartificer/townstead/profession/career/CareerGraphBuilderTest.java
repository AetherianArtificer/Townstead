package com.aetherianartificer.townstead.profession.career;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CareerGraphBuilderTest {

    @Test
    void separatesAboutFromWhatItDoesWithoutDiscardingDerivedEffects() {
        CareerGraphBuilder.SkillCopy copy = CareerGraphBuilder.splitSkillCopy(
                "A practiced hand makes quick work. Deal 6 damage to foes in front of you.",
                List.of("+1 Cook XP"));

        assertEquals("A practiced hand makes quick work.", copy.about());
        assertEquals(List.of("Deal 6 damage to foes in front of you.", "+1 Cook XP"),
                copy.effects());
    }
}
