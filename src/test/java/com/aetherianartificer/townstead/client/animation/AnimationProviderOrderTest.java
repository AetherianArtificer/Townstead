package com.aetherianartificer.townstead.client.animation;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationProviderOrderTest {
    private static final List<AnimationTransform> POSE = List.of(
            AnimationTransform.rotate("body", 0.5f, 0, 0, AnimationTransform.Operation.SET));

    @Test void allEmfProvidersAreTriedBeforeTheFirstVanillaProvider() {
        var tried = new ArrayList<String>();
        var result = EmfAnimationSourceAdapter.resolveTransforms(List.of("minecraft:piglin", "humanoid"), id -> {
            tried.add(id + " EMF");
            return List.of();
        }, id -> {
            tried.add(id + " vanilla");
            return java.util.Optional.of(POSE);
        });
        assertEquals(POSE, result);
        assertEquals(List.of("minecraft:piglin EMF", "minecraft:player EMF", "minecraft:piglin vanilla"), tried);
    }

    @Test void vanillaPassPreservesAuthoredOrderAfterEmfIsExhausted() {
        var tried = new ArrayList<String>();
        var result = EmfAnimationSourceAdapter.resolveTransforms(List.of("example:missing", "humanoid"), id -> {
            tried.add(id + " EMF");
            return List.of();
        }, id -> {
            tried.add(id + " vanilla");
            return id.equals("humanoid") ? java.util.Optional.of(POSE) : java.util.Optional.empty();
        });
        assertEquals(POSE, result);
        assertEquals(List.of("example:missing EMF", "minecraft:player EMF", "example:missing vanilla", "humanoid vanilla"), tried);
    }

    @Test void specificProviderWinsBeforeHumanoid() {
        var tried = new ArrayList<String>();
        assertEquals(POSE, EmfAnimationSourceAdapter.resolveTransforms(List.of("minecraft:piglin", "humanoid"), id -> {
            tried.add(id);
            return POSE;
        }, id -> java.util.Optional.empty()));
        assertEquals(List.of("minecraft:piglin"), tried);
    }

    @Test void humanoidResolvesEmfInItsAuthoredPosition() {
        var tried = new ArrayList<String>();
        assertEquals(POSE, EmfAnimationSourceAdapter.resolveTransforms(List.of("minecraft:piglin", "humanoid"), id -> {
            tried.add(id);
            return id.equals("minecraft:player") ? POSE : List.of();
        }, id -> { fail("Vanilla must not run when a later EMF provider succeeds"); return java.util.Optional.empty(); }));
        assertEquals(List.of("minecraft:piglin", "minecraft:player"), tried);
    }

    @Test void allEmfProvidersPrecedeEvenAnEarlierHumanoidVanillaFallback() {
        var tried = new ArrayList<String>();
        assertTrue(EmfAnimationSourceAdapter.resolveTransforms(List.of("humanoid", "minecraft:piglin"), id -> {
            tried.add(id);
            return List.of();
        }, id -> id.equals("humanoid") ? java.util.Optional.of(List.of()) : java.util.Optional.empty()).isEmpty());
        assertEquals(List.of("minecraft:player", "minecraft:piglin"), tried);
    }

    @Test void noUnauthoredProvidersAreAdded() {
        var tried = new ArrayList<String>();
        assertTrue(EmfAnimationSourceAdapter.resolveTransforms(List.of("minecraft:piglin", "minecraft:zombie"), id -> {
            tried.add(id);
            return List.of();
        }, id -> id.equals("humanoid") ? java.util.Optional.of(List.of()) : java.util.Optional.empty()).isEmpty());
        assertEquals(List.of("minecraft:piglin", "minecraft:zombie"), tried);
        assertEquals(List.of("minecraft:zombie", "minecraft:piglin"),
                GeneAnimations.providerChain(null, List.of("minecraft:zombie", "minecraft:piglin")));
    }
}
