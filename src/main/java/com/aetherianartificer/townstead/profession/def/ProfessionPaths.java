package com.aetherianartificer.townstead.profession.def;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Specialization paths within a profession: a branch opened by buying its gateway skill
 * ("spec into pizzaiolo"). A path names its member skills and the worksites it favours.
 * Players enter by choice and are never steered; villagers enter through circumstance,
 * because their worksite contains the path's stations, and once specced they prefer those
 * stations and finish the build. The completed build usually carries a matching
 * {@link ProfessionTitles title}. Replaced wholesale each datapack reload.
 */
public final class ProfessionPaths {

    /** One specialization branch. {@code skills} excludes the gateway; {@code members} is both. */
    public record Path(ResourceLocation professionId, String id, ResourceLocation gateway,
                       List<ResourceLocation> skills, List<ResourceLocation> worksites) {
        public Path {
            skills = List.copyOf(skills);
            worksites = List.copyOf(worksites);
        }

        public boolean isMember(ResourceLocation skillId) {
            return gateway.equals(skillId) || skills.contains(skillId);
        }
    }

    private static volatile Map<ResourceLocation, List<Path>> BY_PROFESSION = Map.of();

    private ProfessionPaths() {}

    public static void replaceAll(Map<ResourceLocation, List<Path>> next) {
        BY_PROFESSION = Map.copyOf(next);
    }

    public static List<Path> pathsFor(ResourceLocation professionId) {
        return BY_PROFESSION.getOrDefault(professionId, List.of());
    }

    /** The path a skill belongs to within its profession, or null for trunk skills. */
    @Nullable
    public static Path pathOwning(ResourceLocation professionId, ResourceLocation skillId) {
        for (Path path : pathsFor(professionId)) {
            if (path.isMember(skillId)) return path;
        }
        return null;
    }

    /** Every path whose gateway the learned-skill set contains, across all professions. */
    public static List<Path> speccedPaths(Predicate<ResourceLocation> learned) {
        List<Path> out = new ArrayList<>();
        for (List<Path> paths : BY_PROFESSION.values()) {
            for (Path path : paths) {
                if (learned.test(path.gateway())) out.add(path);
            }
        }
        return out;
    }
}
