package com.aetherianartificer.townstead.profession.skill;

import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-entity set of learned skills, the runtime state professions grant capabilities from.
 * Enforces prerequisites and exclusivity on learn. State is transient for now (in memory,
 * keyed by entity UUID); durable persistence and work-event-driven mastery are the next
 * Townstead-integration step. Mirrors the transient per-entity stores already used for
 * ability toggles and stacking effects.
 */
public final class LearnedSkills {

    private static final Map<UUID, Set<ResourceLocation>> STATE = new ConcurrentHashMap<>();

    private LearnedSkills() {}

    public static Set<ResourceLocation> learned(LivingEntity entity) {
        Set<ResourceLocation> set = STATE.get(entity.getUUID());
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    public static boolean has(LivingEntity entity, ResourceLocation skill) {
        Set<ResourceLocation> set = STATE.get(entity.getUUID());
        return set != null && set.contains(skill);
    }

    /** Learn a skill if its prerequisites are met and it is not blocked by exclusivity. */
    public static Result learn(LivingEntity entity, ResourceLocation skillId) {
        SkillDef skill = SkillDefs.byId(skillId);
        if (skill == null) return Result.fail("unknown skill '" + skillId + "'");
        Set<ResourceLocation> set = STATE.computeIfAbsent(entity.getUUID(), u -> new LinkedHashSet<>());
        if (set.contains(skillId)) return Result.fail("already learned");

        for (ResourceLocation req : skill.requires()) {
            if (!set.contains(req)) return Result.fail("missing prerequisite '" + req + "'");
        }
        ResourceLocation conflict = exclusivityConflict(skill, set);
        if (conflict != null) return Result.fail("excluded by learned skill '" + conflict + "'");

        set.add(skillId);
        return Result.success();
    }

    public static boolean forget(LivingEntity entity, ResourceLocation skillId) {
        Set<ResourceLocation> set = STATE.get(entity.getUUID());
        return set != null && set.remove(skillId);
    }

    /** A learned skill that mutually excludes the candidate, or null if none. */
    @Nullable
    private static ResourceLocation exclusivityConflict(SkillDef skill, Set<ResourceLocation> learned) {
        for (ResourceLocation other : skill.exclusiveWith()) {
            if (learned.contains(other)) return other;
        }
        for (ResourceLocation learnedId : learned) {
            SkillDef learnedSkill = SkillDefs.byId(learnedId);
            if (learnedSkill != null && learnedSkill.exclusiveWith().contains(skill.id())) return learnedId;
        }
        return null;
    }

    public record Result(boolean ok, @Nullable String error) {
        static Result success() {
            return new Result(true, null);
        }

        static Result fail(String error) {
            return new Result(false, error);
        }
    }
}
