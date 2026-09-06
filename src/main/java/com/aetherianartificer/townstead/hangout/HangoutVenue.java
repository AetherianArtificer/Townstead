package com.aetherianartificer.townstead.hangout;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A social use of one or more MCA building types. Structural recognition remains MCA's job. */
public record HangoutVenue(ResourceLocation id, Set<String> buildings, int capacity,
                           List<ResourceLocation> activities, Set<ResourceLocation> tags,
                           Set<String> amenities,
                           Map<String, Condition> staffRoles,
                           @Nullable Condition openWhen,
                           @Nullable Condition admissionWhen) {
    public HangoutVenue {
        Objects.requireNonNull(id, "id");
        buildings = buildings == null ? Set.of() : Set.copyOf(buildings);
        activities = activities == null ? List.of() : List.copyOf(activities);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        amenities = amenities == null ? Set.of() : Set.copyOf(amenities);
        staffRoles = staffRoles == null ? Map.of() : Map.copyOf(staffRoles);
        if (buildings.isEmpty()) throw new IllegalArgumentException("buildings must not be empty");
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
    }
}
