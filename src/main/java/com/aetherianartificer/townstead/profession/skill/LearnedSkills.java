package com.aetherianartificer.townstead.profession.skill;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.RetrainingPolicy;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-entity set of learned skills, the runtime state professions grant capabilities from.
 * {@link #learn} enforces prerequisites. Legacy pairwise exclusions no longer erase or block
 * learned history; the Career schema equips one learned option per skill group. XP gating is enforced
 * once progression-driven unlock state is wired (the next slice) and are noted on each method.
 * {@link #forget} always rejects normal removal because learned history is permanent. {@link #forceLearn} and
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
            return TownsteadVillagers.get(villager).professionMemory().careerProfile().learnedChoices();
        }
        if (entity instanceof Player player) {
            return com.aetherianartificer.townstead.profession.career.PlayerCareers.get(player).learnedChoices();
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

    /** Drop only the non-player transient fallback. Persistent player Career history is untouched. */
    public static void clear(UUID uuid) {
        STATE.remove(uuid);
    }

    public static Result learn(LivingEntity entity, ResourceLocation skillId) {
        Result result = learnInto(backing(entity), skillId);
        if (result.ok() && entity instanceof VillagerEntityMCA villager) {
            com.aetherianartificer.townstead.profession.ProfessionClothing.afterPathChange(villager);
        }
        return result;
    }

    public static Result learn(UUID uuid, ResourceLocation skillId) {
        return learnInto(new TransientBacking(uuid), skillId);
    }

    public static Result forceLearn(LivingEntity entity, ResourceLocation skillId) {
        Result result = forceLearnInto(backing(entity), skillId);
        if (result.ok() && entity instanceof VillagerEntityMCA villager) {
            com.aetherianartificer.townstead.profession.ProfessionClothing.afterPathChange(villager);
        }
        return result;
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
     * Learn a skill, enforcing prerequisites. Tier / unlock model / XP
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
     *
     * <p>This used to refuse outright, on the reasoning that a career is history and history does
     * not un-happen. That held while skills were bought with banked points and nothing was ever
     * truly closed off. It does not hold now: a level offers several options and taking one shuts
     * the others, so retraining is the ONLY way to revisit a choice, and refusing it would make
     * every pick permanent for the life of the character. A profession can still forbid it with
     * {@code "retraining": "locked"}, which is now a deliberate statement about that career
     * rather than the silent default it was.</p>
     */
    private static ForgetResult forgetFrom(Backing backing, ResourceLocation skillId) {
        if (!backing.contains(skillId)) return ForgetResult.fail("not learned");
        SkillDef skill = SkillDefs.byId(skillId);
        ProfessionDef owner = skill == null ? null : ProfessionDefs.byId(skill.profession());
        if (owner != null && owner.retraining() == RetrainingPolicy.LOCKED) {
            // Name may be absent on a def built in code rather than parsed from a pack.
            String who = owner.displayName() == null
                    ? owner.id().toString() : owner.displayName().getString();
            return ForgetResult.fail("'" + who + "' does not allow retraining");
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
        if (entity instanceof Player player) return new PlayerBacking(player);
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
        @Override public Set<ResourceLocation> view() { return memory.careerProfile().learnedChoices(); }
        @Override public boolean contains(ResourceLocation id) { return memory.hasSkill(id); }
        @Override public void add(ResourceLocation id) { memory.addSkill(id); }
        @Override public void remove(ResourceLocation id) { memory.removeSkill(id); }
    }

    private record PlayerBacking(Player player) implements Backing {
        @Override public Set<ResourceLocation> view() {
            return com.aetherianartificer.townstead.profession.career.PlayerCareers.get(player).learnedChoices();
        }
        @Override public boolean contains(ResourceLocation id) { return view().contains(id); }
        @Override public void add(ResourceLocation id) {
            com.aetherianartificer.townstead.profession.career.PlayerCareers.mutate(
                    player, profile -> profile.learnChoice(id));
        }
        @Override public void remove(ResourceLocation id) {
            com.aetherianartificer.townstead.profession.career.PlayerCareers.mutate(
                    player, profile -> profile.adminForgetChoice(id));
        }
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
