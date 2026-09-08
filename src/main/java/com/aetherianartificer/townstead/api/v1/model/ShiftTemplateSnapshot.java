package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** A shift template: 24 hourly ordinals, optionally tied to a chronotype. */
public record ShiftTemplateSnapshot(
        ResourceLocation id,
        String displayName,
        List<Integer> shifts,
        Optional<String> chronotype,
        boolean builtIn
) {
    public ShiftTemplateSnapshot {
        shifts = shifts == null ? List.of() : List.copyOf(shifts);
        chronotype = chronotype == null ? Optional.empty() : chronotype;
    }
}
