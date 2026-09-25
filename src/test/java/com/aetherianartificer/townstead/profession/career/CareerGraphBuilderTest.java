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

    @Test
    void exclamationsAndQuestionsEndTheAboutSentence() {
        assertEquals("Wake up!", CareerGraphBuilder.splitSkillCopy(
                "Wake up! Gain Speed for 10 seconds.", List.of()).about());
        assertEquals("Who?", CareerGraphBuilder.splitSkillCopy(
                "Who? Wear a disguise.", List.of()).about());
        assertEquals("Bouh !", CareerGraphBuilder.splitSkillCopy(
                "Bouh ! Repoussez-les.", List.of()).about());
    }

    @Test
    void decimalsAndFullWidthStopsAreHandled() {
        assertEquals("Heal 1.5 hearts.", CareerGraphBuilder.splitSkillCopy(
                "Heal 1.5 hearts. Then rest.", List.of()).about());
        assertEquals("快跑。", CareerGraphBuilder.splitSkillCopy(
                "快跑。获得速度。", List.of()).about());
    }
}
