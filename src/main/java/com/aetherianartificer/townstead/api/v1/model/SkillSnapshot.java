package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** A registered skill definition. */
public record SkillSnapshot(
        ResourceLocation id,
        Component displayName,
        Component description,
        ResourceLocation profession,
        int tier,
        int cost,
        List<ResourceLocation> requires,
        List<ResourceLocation> exclusiveWith,
        Optional<ResourceLocation> skillGroup
) {
    public SkillSnapshot {
        requires = requires == null ? List.of() : List.copyOf(requires);
        exclusiveWith = exclusiveWith == null ? List.of() : List.copyOf(exclusiveWith);
        skillGroup = skillGroup == null ? Optional.empty() : skillGroup;
    }
}
