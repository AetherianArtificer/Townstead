package com.aetherianartificer.townstead.profession.def;

import net.minecraft.network.chat.Component;
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

    /**
     * One specialization branch. {@code skills} excludes the gateway; {@code members} is both.
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
                       int color, @Nullable ResourceLocation backdrop) {
        public Path {
            skills = List.copyOf(skills);
            worksites = List.copyOf(worksites);
        }

        /** Compatibility constructor predating the board's section styling. */
        public Path(ResourceLocation professionId, String id, Component displayName,
                    ResourceLocation gateway,
                    List<ResourceLocation> skills, List<ResourceLocation> worksites) {
            this(professionId, id, displayName, gateway, skills, worksites, 0, null);
        }

        public boolean isMember(ResourceLocation skillId) {
            return gateway.equals(skillId) || skills.contains(skillId);
        }

        /** Every skill on the path: the opening option plus the rest, in authoring order. */
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

    /** The path a skill belongs to within its profession, or null for trunk skills. */
    @Nullable
    public static Path pathOwning(ResourceLocation professionId, ResourceLocation skillId) {
        for (Path path : pathsFor(professionId)) {
            if (path.isMember(skillId)) return path;
        }
        return null;
    }

    /**
     * The one path this character committed to within a profession, or null while the choice is
     * still open. Commitment is owning ANY option on the path, not a designated gateway: under
     * the levels-and-options model your first pick is your path choice, so a separate opening
     * skill would be a mechanic with nothing left to do. There is still no stored decision that
     * can drift out of sync with the learned set.
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
     * The options a path offers at one level: every member skill whose tier is that level. The
     * board draws these as a row and exactly one of them may be owned, which is what makes five
     * levels mean five choices. Authors do not list options per level anywhere; a skill's tier
     * already says when it is offered, so there is only one place to change it.
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
