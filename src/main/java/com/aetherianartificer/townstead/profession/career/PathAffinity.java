package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionPaths;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.skill.LearnedSkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * How villagers relate to specialization paths. Players have free will: they buy a path's
 * gateway skill or they don't, and nothing here applies to them. Villagers spec by
 * circumstance: a path's skills only enter their auto-spend pool when their worksite
 * actually contains the path's stations, a committed villager (gateway learned) is weighted
 * toward finishing the build, and a specced villager prefers the path's stations when
 * choosing work. Worksite contents are read through a probe the active work compat
 * registers, so this layer stays free of any one mod's kitchen model.
 */
public final class PathAffinity {

    /** villager, path worksite block ids → does the villager's worksite contain any of them. */
    private static volatile BiPredicate<LivingEntity, List<ResourceLocation>> WORKSITE_PROBE;

    private PathAffinity() {}

    public static void registerWorksiteProbe(BiPredicate<LivingEntity, List<ResourceLocation>> probe) {
        WORKSITE_PROBE = probe;
    }

    /**
     * Auto-spend weight for one candidate skill: skills belonging to no path weigh 1; a path's
     * skills weigh 4 once the villager already owns something on that path (finish what you
     * started), 3 when the path's stations stand in their worksite (the pizzeria pulls its cook
     * into the craft), and 0 otherwise so picks are never wasted on stations they cannot reach.
     *
     * <p>Keyed on owning ANY member rather than a designated first skill: paths have no gateway
     * to own, and a villager who took the path's level-two option and nothing else is every bit
     * as committed as one who started at level one.</p>
     */
    static int autoSpendWeight(LivingEntity villager, ProfessionDef def, SkillDef skill) {
        ProfessionPaths.Path path = ProfessionPaths.pathOwning(def.id(), skill.id());
        if (path == null) return 1;
        for (ResourceLocation member : path.members()) {
            if (LearnedSkills.has(villager, member)) return 4;
        }
        return worksiteHasAny(villager, path.worksites()) ? 3 : 0;
    }

    /** Station blocks the entity's specced paths favour; empty when unspecced (or a player). */
    public static Set<ResourceLocation> preferredWorksites(LivingEntity villager) {
        Set<ResourceLocation> learned = LearnedSkills.learned(villager);
        if (learned.isEmpty()) return Set.of();
        Set<ResourceLocation> out = new HashSet<>();
        for (ProfessionPaths.Path path : ProfessionPaths.speccedPaths(learned::contains)) {
            out.addAll(path.worksites());
        }
        return out;
    }

    private static boolean worksiteHasAny(LivingEntity villager, List<ResourceLocation> worksites) {
        BiPredicate<LivingEntity, List<ResourceLocation>> probe = WORKSITE_PROBE;
        return probe != null && !worksites.isEmpty() && probe.test(villager, worksites);
    }
}
