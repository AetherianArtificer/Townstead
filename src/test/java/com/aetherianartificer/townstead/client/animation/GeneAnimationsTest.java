package com.aetherianartificer.townstead.client.animation;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneAnimationsTest {
    @Test void creativeFlightAndGlidingBothResolveTheConfiguredFlySource() {
        var enabled = new com.aetherianartificer.townstead.root.Animations(java.util.Map.of(
                com.aetherianartificer.townstead.root.Animations.State.FLY,
                com.aetherianartificer.townstead.root.Animations.Source.HUMANOID), List.of("minecraft:piglin", "humanoid"));
        assertTrue(GeneAnimations.usesHumanoidStatePose(false, false, false, true, enabled));
        assertTrue(GeneAnimations.usesHumanoidStatePose(false, false, true, false, enabled));
        assertFalse(GeneAnimations.usesHumanoidStatePose(false, false, false, false, enabled));
        var disabled = new com.aetherianartificer.townstead.root.Animations(java.util.Map.of(
                com.aetherianartificer.townstead.root.Animations.State.FLY,
                com.aetherianartificer.townstead.root.Animations.Source.NONE), enabled.providers());
        assertFalse(GeneAnimations.usesHumanoidStatePose(false, false, false, true, disabled));
        assertFalse(GeneAnimations.usesHumanoidStatePose(false, false, true, false, disabled));
        assertEquals(List.of("humanoid"), GeneAnimations.providerChain("minecraft:zombie;humanoid",
                enabled.providers(), GeneAnimations.usesHumanoidStatePose(false, false, false, true, enabled)));
    }
    @Test void humanoidCrouchAndSleepTakePriorityOnlyInTheirAuthoredStates() {
        var humanoid = com.aetherianartificer.townstead.root.Animations.DEFAULT;
        assertTrue(GeneAnimations.usesHumanoidStatePose(false, false, true, humanoid));
        assertTrue(GeneAnimations.usesHumanoidStatePose(true, false, humanoid));
        assertTrue(GeneAnimations.usesHumanoidStatePose(false, true, humanoid));
        assertFalse(GeneAnimations.usesHumanoidStatePose(false, false, humanoid));
        var disabled = new com.aetherianartificer.townstead.root.Animations(java.util.Map.of(
                com.aetherianartificer.townstead.root.Animations.State.CROUCH,
                com.aetherianartificer.townstead.root.Animations.Source.NONE,
                com.aetherianartificer.townstead.root.Animations.State.SLEEP,
                com.aetherianartificer.townstead.root.Animations.Source.NONE), List.of());
        assertFalse(GeneAnimations.usesHumanoidStatePose(true, false, disabled));
        assertFalse(GeneAnimations.usesHumanoidStatePose(false, true, disabled));
    }
    @Test void crouchAndSleepTryHumanoidEmfBeforeVanilla() {
        assertEquals(List.of("humanoid"),
                GeneAnimations.providerChain("minecraft:zombie", List.of("minecraft:piglin"), true));
    }

    @Test void zombieTriesItsOwnEmfIdentityThenTheVanillaBase() {
        assertEquals(List.of("minecraft:zombie", "humanoid"),
                GeneAnimations.providerChain("minecraft:zombie;humanoid", List.of("minecraft:piglin", "minecraft:player")));
    }

    @Test void curingRestoresTheSpeciesProvidersAndUnauthoredSpeciesKeepTheirDefault() {
        var species = List.of("minecraft:piglin", "humanoid");
        assertEquals(species, GeneAnimations.providerChain(null, species));
        assertEquals(List.of(), GeneAnimations.providerChain(null, List.of()));
    }
}
