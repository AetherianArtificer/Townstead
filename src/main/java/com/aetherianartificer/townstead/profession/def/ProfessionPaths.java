package com.aetherianartificer.townstead.profession.def;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.aetherianartificer.townstead.pheno.power.PowerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Specialization paths within a profession. A path groups member skills, while each skill's
 * authored prerequisites define the actual parent-child hierarchy. It also names the worksites
 * it favours and any Pheno components shared by every build on the path.
 * Players enter by choice and are never steered; villagers enter through circumstance,
 * because their worksite contains the path's stations, and once specced they prefer those
 * stations and finish the build. The completed build usually carries a matching
 * {@link ProfessionTitles title}. Replaced wholesale each datapack reload.
 */
public final class ProfessionPaths {

    /**
     * One specialization branch. {@code gateway} is the first flattened member retained by the
     * established runtime shape; investment is still detected from any member. {@code skills}
     * contains the remaining options, and {@code members} is both. {@code powers} are expressed
     * once whenever any member is learned.
     *
     * <p>{@code color} and {@code backdrop} are the path's own look on the Career board, where it
     * occupies a titled section of the page. A pack that adds a path should be able to give it a
     * character rather than being handed the next colour off a fixed palette, so both are
     * authored: {@code color} washes the section, {@code backdrop} replaces that wash with a
     * texture. Either may be absent, and the board falls back to a rotating palette.</p>
     */
    public record Path(ResourceLocation professionId, String id, Component displayName,
                       ResourceLocation gateway,
                       List<ResourceLocation> skills, List<ResourceLocation> worksites,
                       int color, @Nullable ResourceLocation backdrop,
                       List<PowerComponent> powers, List<ClothingChoice> clothing) {
        public Path {
            skills = List.copyOf(skills);
            worksites = List.copyOf(worksites);
            powers = List.copyOf(powers);
            clothing = List.copyOf(clothing);
        }

        /** Compatibility constructor predating the board's section styling. */
        public Path(ResourceLocation professionId, String id, Component displayName,
                    ResourceLocation gateway,
                    List<ResourceLocation> skills, List<ResourceLocation> worksites) {
            this(professionId, id, displayName, gateway, skills, worksites, 0, null,
                    List.of(), List.of());
        }

        public Path(ResourceLocation professionId, String id, Component displayName,
                    ResourceLocation gateway, List<ResourceLocation> skills,
                    List<ResourceLocation> worksites, int color,
                    @Nullable ResourceLocation backdrop) {
            this(professionId, id, displayName, gateway, skills, worksites,
                    color, backdrop, List.of(), List.of());
        }

        public boolean isMember(ResourceLocation skillId) {
            return gateway.equals(skillId) || skills.contains(skillId);
        }

        /** Every skill on the path: the first compatibility member plus the rest, in authoring order. */
        public List<ResourceLocation> members() {
            List<ResourceLocation> all = new ArrayList<>(skills.size() + 1);
            all.add(gateway);
            for (ResourceLocation skill : skills) {
                if (!skill.equals(gateway)) all.add(skill);
            }
            return all;
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

    /** A Path by its author id within one Profession. */
    @Nullable
    public static Path byId(ResourceLocation professionId, String pathId) {
        if (pathId == null) return null;
        for (Path path : pathsFor(professionId)) {
            if (path.id().equals(pathId)) return path;
        }
        return null;
    }

    /** The path a skill belongs to within its profession, or null for trunk skills. */
    @Nullable
    public static Path pathOwning(ResourceLocation professionId, ResourceLocation skillId) {
        for (Path path : pathsFor(professionId)) {
            if (path.isMember(skillId)) return path;
        }
        return null;
    }

    /**
     * The first invested path in authoring order, retained for singular legacy callers. Returning
     * one path here does not lock the others: characters may invest across paths, and
     * {@link #speccedPaths(Predicate)} returns the complete set. There is no stored path decision
     * that can drift out of sync with the learned skills.
     */
    @Nullable
    public static Path committedPath(ResourceLocation professionId,
                                     Predicate<ResourceLocation> learned) {
        for (Path path : pathsFor(professionId)) {
            if (owns(path, learned)) return path;
        }
        return null;
    }

    /** Every path this character has taken any option on, across all professions. */
    public static List<Path> speccedPaths(Predicate<ResourceLocation> learned) {
        List<Path> out = new ArrayList<>();
        for (List<Path> paths : BY_PROFESSION.values()) {
            for (Path path : paths) {
                if (owns(path, learned)) out.add(path);
            }
        }
        return out;
    }

    private static boolean owns(Path path, Predicate<ResourceLocation> learned) {
        if (learned.test(path.gateway())) return true;
        for (ResourceLocation skill : path.skills()) {
            if (learned.test(skill)) return true;
        }
        return false;
    }

    /**
     * Every path member displayed in one rank band. This grouping controls placement and rank
     * eligibility only; it does not make the members mutually exclusive or connect one band to
     * the next. Parent-child order belongs exclusively to {@link SkillDef#requires()}.
     */
    public static List<ResourceLocation> optionsAt(Path path, int level) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : path.members()) {
            SkillDef skill = SkillDefs.byId(id);
            if (skill != null && skill.tier() == level) out.add(id);
        }
        return out;
    }
}
