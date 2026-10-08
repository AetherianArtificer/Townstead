package com.aetherianartificer.townstead.politics.founding;

import com.aetherianartificer.townstead.culture.Cultures;
import com.aetherianartificer.townstead.politics.definition.FactionKind;
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
        if (profile.faction() == null) return List.copyOf(errors);
        FactionKind kind = PoliticalDefinitions.snapshot().kind(profile.faction().kind());
        if (kind == null) {
            errors.add("unknown faction kind " + profile.faction().kind());
            return List.copyOf(errors);
        }
        if (profile.faction().legacy()) return List.copyOf(errors);
        for (FoundingProfileDefinition.Seat seat : profile.faction().seats()) {
            for (ResourceLocation office : seat.offices()) {
                if (kind.office(office) == null) errors.add("seat names " + office + " which is not an office of " + kind.id());
            }
        }
        return List.copyOf(errors);
    }
}
