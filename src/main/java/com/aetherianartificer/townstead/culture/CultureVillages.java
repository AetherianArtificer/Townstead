package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.politics.founding.FoundingProfileDefinition;
import com.aetherianartificer.townstead.politics.founding.FoundingProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Village structures that belong to a culture or to one of its founding profiles, from the optional
 * {@code villages} list in culture and founding profile data. A village generated as one of these
 * structures founds as that culture, and the Switchboard's culture toggle and rate decide whether
 * and how often the structure generates at all.
 *
 * <pre>{@code
 * "villages": ["townstead_cavalrosia:village/town"]
 * }</pre>
 */
public final class CultureVillages {
    private static volatile Map<ResourceLocation, ResourceLocation> cultureByStructure = Map.of();
    private static volatile Map<ResourceLocation, List<ResourceLocation>> profilesByStructure = Map.of();

    private CultureVillages() {}

    static void replaceCultures(Map<ResourceLocation, ResourceLocation> byStructure) {
        cultureByStructure = Map.copyOf(byStructure);
    }

    public static void replaceProfiles(Map<ResourceLocation, List<ResourceLocation>> byStructure) {
        Map<ResourceLocation, List<ResourceLocation>> copy = new LinkedHashMap<>();
        byStructure.forEach((structure, profiles) -> copy.put(structure, List.copyOf(profiles)));
        profilesByStructure = Map.copyOf(copy);
    }

    /** The culture a village structure belongs to, directly or through the profiles that claim it. */
    public static @Nullable ResourceLocation culture(ResourceLocation structure) {
        ResourceLocation culture = cultureByStructure.get(structure);
        if (culture != null) return culture;
        for (ResourceLocation id : profiles(structure)) {
            FoundingProfileDefinition profile = FoundingProfiles.get(id);
            if (profile != null && profile.culture() != null) return profile.culture();
        }
        return null;
    }

    /** Founding profiles that claim a village structure for themselves. */
    public static List<ResourceLocation> profiles(ResourceLocation structure) {
        return profilesByStructure.getOrDefault(structure, List.of());
    }

    /** A village structure for a profile: one it claims itself first, then one its culture claims. */
    public static @Nullable ResourceLocation structureFor(@Nullable FoundingProfileDefinition profile) {
        if (profile == null) return null;
        for (Map.Entry<ResourceLocation, List<ResourceLocation>> entry : profilesByStructure.entrySet()) {
            if (entry.getValue().contains(profile.id())) return entry.getKey();
        }
        for (Map.Entry<ResourceLocation, ResourceLocation> entry : cultureByStructure.entrySet()) {
            if (Cultures.within(profile.culture(), entry.getValue())) return entry.getKey();
        }
        return null;
    }

    /** The cultural village structure whose bounds hold {@code pos}, if any. */
    public static @Nullable ResourceLocation structureAt(ServerLevel level, BlockPos pos) {
        Set<ResourceLocation> structures = new LinkedHashSet<>(profilesByStructure.keySet());
        structures.addAll(cultureByStructure.keySet());
        if (structures.isEmpty()) return null;
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (ResourceLocation id : structures) {
            Structure structure = registry.get(id);
            if (structure != null && level.structureManager().getStructureAt(pos, structure).isValid()) return id;
        }
        return null;
    }

    /**
     * Whether a structure may start in this chunk. Structures outside any culture always may; a
     * culture switched off in the Switchboard generates none, and a rate below one thins them out
     * with a roll fixed by the world seed and chunk.
     */
    public static boolean allowsGeneration(ResourceLocation structure, long seed, ChunkPos chunk) {
        ResourceLocation culture = culture(structure);
        if (culture == null) return true;
        String id = culture.toString();
        if (!CultureRules.enabled(id)) return false;
        double rate = CultureRules.rate(id);
        if (rate >= 1.0) return true;
        RandomSource roll = RandomSource.create(seed ^ chunk.toLong() ^ ((long) structure.hashCode() << 32));
        return roll.nextDouble() < rate;
    }
}
