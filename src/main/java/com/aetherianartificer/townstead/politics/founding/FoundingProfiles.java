package com.aetherianartificer.townstead.politics.founding;

import com.aetherianartificer.townstead.culture.Cultures;
import com.aetherianartificer.townstead.politics.definition.OrganizationKindDefinition;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable live view of every loaded founding profile. */
public final class FoundingProfiles {
    private static volatile Map<ResourceLocation, FoundingProfileDefinition> ENTRIES = Map.of();
    private static volatile Map<ResourceLocation, ResourceLocation> LEGACY_IDS = Map.of();

    private FoundingProfiles() {}

    static void replace(Map<ResourceLocation, FoundingProfileDefinition> profiles) {
        replace(profiles, Map.of());
    }

    static void replace(Map<ResourceLocation, FoundingProfileDefinition> profiles,
                        Map<ResourceLocation, ResourceLocation> aliases) {
        ENTRIES = Map.copyOf(new LinkedHashMap<>(profiles));
        LEGACY_IDS = Map.copyOf(new LinkedHashMap<>(aliases));
    }

    public static @Nullable FoundingProfileDefinition get(@Nullable ResourceLocation id) {
        if (id == null) return null;
        FoundingProfileDefinition direct = ENTRIES.get(id);
        if (direct != null) return direct;
        ResourceLocation canonical = LEGACY_IDS.get(id);
        return canonical == null ? null : ENTRIES.get(canonical);
    }

    public static @Nullable ResourceLocation canonicalId(@Nullable ResourceLocation id) {
        if (id == null || ENTRIES.containsKey(id)) return id;
        return LEGACY_IDS.getOrDefault(id, id);
    }

    public static List<FoundingProfileDefinition> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static List<ResourceLocation> ids() {
        return List.copyOf(ENTRIES.keySet());
    }

    /** Cross-document errors which make a profile unsafe to instantiate. */
    public static List<String> validate(FoundingProfileDefinition profile) {
        List<String> errors = new ArrayList<>();
        if (profile.culture() != null && Cultures.get(profile.culture()) == null) {
            errors.add("unknown culture " + profile.culture());
        }
        if (profile.government() == null) return List.copyOf(errors);
        PoliticalDefinitions.Snapshot definitions = PoliticalDefinitions.snapshot();
        OrganizationKindDefinition kind = definitions.organizationKind(profile.government().organizationKind());
        if (kind == null) {
            errors.add("unknown government organization kind " + profile.government().organizationKind());
            return List.copyOf(errors);
        }
        var bound = kind.roles().stream().map(OrganizationKindDefinition.RoleBinding::role)
                .collect(java.util.stream.Collectors.toSet());
        for (FoundingProfileDefinition.Seat seat : profile.government().seats()) {
            for (ResourceLocation role : seat.roles()) {
                if (!bound.contains(role)) errors.add("government seat uses role " + role
                        + " which is not bound by " + kind.id());
            }
        }
        return List.copyOf(errors);
    }
}
