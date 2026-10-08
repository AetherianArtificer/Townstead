package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** A registered profession definition. */
public record ProfessionSnapshot(
        String id,
        Component displayName,
        Component description,
        Optional<ProgressionTrackSnapshot> track,
        List<ResourceLocation> skills,
        List<String> aliases,
        List<String> levelNames,
        boolean hidden
) {
    public ProfessionSnapshot {
        track = track == null ? Optional.empty() : track;
        skills = skills == null ? List.of() : List.copyOf(skills);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        levelNames = levelNames == null ? List.of() : List.copyOf(levelNames);
    }

    public boolean progressive() {
        return track.isPresent();
    }
}
