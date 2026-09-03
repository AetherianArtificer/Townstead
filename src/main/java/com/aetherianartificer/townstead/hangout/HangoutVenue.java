package com.aetherianartificer.townstead.hangout;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A social use of one or more MCA building types. Structural recognition remains MCA's job. */
public record HangoutVenue(ResourceLocation id, Set<String> buildings, int capacity,
                           List<ResourceLocation> activities, Set<String> amenities,
                           @Nullable Condition openWhen) {
    public HangoutVenue {
        Objects.requireNonNull(id, "id");
        buildings = buildings == null ? Set.of() : Set.copyOf(buildings);
        activities = activities == null ? List.of() : List.copyOf(activities);
        amenities = amenities == null ? Set.of() : Set.copyOf(amenities);
        if (buildings.isEmpty()) throw new IllegalArgumentException("buildings must not be empty");
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
    }
}
