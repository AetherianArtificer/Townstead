package com.aetherianartificer.townstead.profession.skill;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
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
 * {@link #learn} enforces the declared model that is checkable from the skill graph today
 * (prerequisites and exclusivity); tier, point cost, unlock model, and XP gating are enforced
 * once progression-driven unlock state is wired (the next slice) and are noted on each method.
 * {@link #forget} respects the profession's retraining policy and cascades, so dropping a
 * prerequisite also drops everything that depended on it; LOCKED is permanent and COSTLY is
 * rejected until its payment mechanism exists (never silently free). {@link #forceLearn} and
 * {@link #forceForget} are explicit admin bypasses.
 *
 * <p>For MCA villagers the state is durable: it lives in
 * {@link TownsteadVillager.ProfessionMemory} and persists with the rest of their typed state.
 * For other entities (players, mobs an admin targets directly) it falls back to a transient
 * UUID-keyed map cleared on logout/death, mirroring the toggles already kept that way.
 */
public final class LearnedSkills {

    private static final Map<UUID, Set<ResourceLocation>> STATE = new ConcurrentHashMap<>();

    private LearnedSkills() {}

    public static Set<ResourceLocation> learned(LivingEntity entity) {
        if (entity instanceof VillagerEntityMCA villager) {
            return TownsteadVillagers.get(villager).professionMemory().learnedSkills();
        }
        return learned(entity.getUUID());
    }

    public static Set<ResourceLocation> learned(UUID uuid) {
        Set<ResourceLocation> set = STATE.get(uuid);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    public static boolean has(LivingEntity entity, ResourceLocation skill) {
        return backing(entity).contains(skill);
    }

    public static boolean has(UUID uuid, ResourceLocation skill) {
        Set<ResourceLocation> set = STATE.get(uuid);
        return set != null && set.contains(skill);
    }

    /** Drop a transient entity's learned set. Wired to player logout so the fallback map stays bounded. */
    public static void clear(UUID uuid) {
        STATE.remove(uuid);
    }

    public static Result learn(LivingEntity entity, ResourceLocation skillId) {
        return learnInto(backing(entity), skillId);
    }

    public static Result learn(UUID uuid, ResourceLocation skillId) {
        return learnInto(new TransientBacking(uuid), skillId);
    }

    public static Result forceLearn(LivingEntity entity, ResourceLocation skillId) {
        return forceLearnInto(backing(entity), skillId);
    }

    public static Result forceLearn(UUID uuid, ResourceLocation skillId) {
        return forceLearnInto(new TransientBacking(uuid), skillId);
    }

    public static ForgetResult forget(LivingEntity entity, ResourceLocation skillId) {
        return forgetFrom(backing(entity), skillId);
    }

    public static ForgetResult forget(UUID uuid, ResourceLocation skillId) {
        return forgetFrom(new TransientBacking(uuid), skillId);
    }

    public static ForgetResult forceForget(LivingEntity entity, ResourceLocation skillId) {
        return forceForgetFrom(backing(entity), skillId);
    }

    public static ForgetResult forceForget(UUID uuid, ResourceLocation skillId) {
        return forceForgetFrom(new TransientBacking(uuid), skillId);
    }

    /**
     * Learn a skill, enforcing prerequisites and exclusivity. Tier / points / unlock model / XP
     * gating is enforced once progression-driven unlock state lands; use {@link #forceLearn} to
     * bypass for admin setup.
     */
    private static Result learnInto(Backing backing, ResourceLocation skillId) {
        SkillDef skill = SkillDefs.byId(skillId);
        if (skill == null) return Result.fail("unknown skill '" + skillId + "'");
        Set<ResourceLocation> set = backing.view();
        if (set.contains(skillId)) return Result.fail("already learned");
        for (ResourceLocation req : skill.requires()) {
            if (!set.contains(req)) return Result.fail("missing prerequisite '" + req + "'");
        }
        ResourceLocation conflict = exclusivityConflict(skill, set);
        if (conflict != null) return Result.fail("excluded by learned skill '" + conflict + "'");
        backing.add(skillId);
        return Result.success();
    }

    /** Admin bypass: record a learned skill without prerequisite or exclusivity checks. */
    private static Result forceLearnInto(Backing backing, ResourceLocation skillId) {
        if (SkillDefs.byId(skillId) == null) return Result.fail("unknown skill '" + skillId + "'");
        backing.add(skillId);
        return Result.success();
    }

    /**
     * Forget a skill, honoring the profession's retraining policy and cascading to every learned
     * skill that (transitively) required it, so the learned set never becomes graph-invalid.
     */
    private static ForgetResult forgetFrom(Backing backing, ResourceLocation skillId) {
        if (!backing.contains(skillId)) return ForgetResult.fail("not learned");
        SkillDef skill = SkillDefs.byId(skillId);
        if (skill != null) {
            ProfessionDef profession = ProfessionDefs.byId(skill.profession());
            if (profession != null) {
                switch (profession.retraining()) {
                    case LOCKED -> {
                        return ForgetResult.fail("retraining is locked for this profession");
                    }
                    // The cost (resources/time) is Townstead-owned and not built yet; until it is,
                    // costly retraining is rejected rather than silently treated as free.
                    case COSTLY -> {
                        return ForgetResult.fail("costly retraining is not available yet");
                    }
                    default -> { }
                }
            }
        }
        return ForgetResult.removed(cascadeRemove(backing, skillId));
    }

    /** Admin bypass: forget regardless of retraining policy; still cascades to dependents. */
    private static ForgetResult forceForgetFrom(Backing backing, ResourceLocation skillId) {
        if (!backing.contains(skillId)) return ForgetResult.fail("not learned");
        return ForgetResult.removed(cascadeRemove(backing, skillId));
    }

    /** Remove the skill and then, to a fixpoint, any learned skill whose prerequisites are no longer met. */
    private static Set<ResourceLocation> cascadeRemove(Backing backing, ResourceLocation skillId) {
        Set<ResourceLocation> removed = new LinkedHashSet<>();
        backing.remove(skillId);
        removed.add(skillId);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ResourceLocation learnedId : new LinkedHashSet<>(backing.view())) {
                SkillDef learnedSkill = SkillDefs.byId(learnedId);
                if (learnedSkill != null && !backing.view().containsAll(learnedSkill.requires())) {
                    backing.remove(learnedId);
                    removed.add(learnedId);
                    changed = true;
                }
            }
        }
        return removed;
    }

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

    private static Backing backing(LivingEntity entity) {
        if (entity instanceof VillagerEntityMCA villager) {
            return new MemoryBacking(TownsteadVillagers.get(villager).professionMemory());
        }
        return new TransientBacking(entity.getUUID());
    }

    /** Mutable view of one entity's learned set, so the learn/forget logic is storage-agnostic. */
    private interface Backing {
        Set<ResourceLocation> view();
        boolean contains(ResourceLocation id);
        void add(ResourceLocation id);
        void remove(ResourceLocation id);
    }

    /** Durable backing: the villager's persisted {@link TownsteadVillager.ProfessionMemory}. */
    private record MemoryBacking(TownsteadVillager.ProfessionMemory memory) implements Backing {
        @Override public Set<ResourceLocation> view() { return memory.learnedSkills(); }
        @Override public boolean contains(ResourceLocation id) { return memory.hasSkill(id); }
        @Override public void add(ResourceLocation id) { memory.addSkill(id); }
        @Override public void remove(ResourceLocation id) { memory.removeSkill(id); }
    }

    /** Transient fallback for players and other non-villager entities. */
    private record TransientBacking(UUID uuid) implements Backing {
        @Override public Set<ResourceLocation> view() {
            Set<ResourceLocation> set = STATE.get(uuid);
            return set == null ? Set.of() : set;
        }
        @Override public boolean contains(ResourceLocation id) {
            Set<ResourceLocation> set = STATE.get(uuid);
            return set != null && set.contains(id);
        }
        @Override public void add(ResourceLocation id) {
            STATE.computeIfAbsent(uuid, u -> new LinkedHashSet<>()).add(id);
        }
        @Override public void remove(ResourceLocation id) {
            Set<ResourceLocation> set = STATE.get(uuid);
            if (set != null) set.remove(id);
        }
    }

    public record Result(boolean ok, @Nullable String error) {
        static Result success() {
            return new Result(true, null);
        }

        static Result fail(String error) {
            return new Result(false, error);
        }
    }

    public record ForgetResult(boolean ok, @Nullable String error, Set<ResourceLocation> removed) {
        static ForgetResult removed(Set<ResourceLocation> removed) {
            return new ForgetResult(true, null, removed);
        }

        static ForgetResult fail(String error) {
            return new ForgetResult(false, error, Set.of());
        }
    }
}
