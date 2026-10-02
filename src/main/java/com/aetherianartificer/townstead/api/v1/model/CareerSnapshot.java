package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An entity's career profile, the same shape for villagers and players: the vocation it
 * practises, every career it has held or acquired, its learned skill choices, and its progress in
 * each career it has XP in.
 */
public record CareerSnapshot(
        Optional<String> primaryVocation,
        Set<String> careerHistory,
        Set<String> acquiredCareers,
        Set<String> discoveries,
        Set<ResourceLocation> learnedSkills,
        Map<ResourceLocation, ResourceLocation> activeBySkillGroup,
        long lastVocationChangeDay,
        List<ProfessionProgressSnapshot> progress
) {
    public CareerSnapshot {
        primaryVocation = primaryVocation == null ? Optional.empty() : primaryVocation;
        careerHistory = careerHistory == null ? Set.of() : Set.copyOf(careerHistory);
        acquiredCareers = acquiredCareers == null ? Set.of() : Set.copyOf(acquiredCareers);
        discoveries = discoveries == null ? Set.of() : Set.copyOf(discoveries);
        learnedSkills = learnedSkills == null ? Set.of() : Set.copyOf(learnedSkills);
        activeBySkillGroup = activeBySkillGroup == null ? Map.of() : Map.copyOf(activeBySkillGroup);
        progress = progress == null ? List.of() : List.copyOf(progress);
    }
}
