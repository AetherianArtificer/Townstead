package com.aetherianartificer.townstead.reaction;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.reaction.backend.EmoteDurationIndex;
import com.aetherianartificer.townstead.reaction.backend.EmotecraftReactionBackend;
import com.aetherianartificer.townstead.reaction.backend.ReactionBackend;
import com.aetherianartificer.townstead.reaction.backend.ReactionBackends;
import com.aetherianartificer.townstead.reaction.effect.ReactionSideEffects;
import com.aetherianartificer.townstead.reaction.trigger.types.ContextEnterTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.IdleSpotTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.MirrorOfTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.PlayerGestureTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.TaskTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.TimeTriggerType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Central server-side entry point for triggering a reaction. Trigger
 * sources (debug command, gesture handler, task lifecycle, etc.) call
 * {@link #fire(ServerLevel, LivingEntity, ResourceLocation, ReactionContext)};
 * the dispatcher gates by cooldown/lock/chance, scores bindings by
 * personality weight + binding chance, picks one weighted, hands off to
 * the matching {@link ReactionBackend}, then emits side effects.
 */
public final class ReactionDispatcher {
    private ReactionDispatcher() {}

    public static boolean fire(ServerLevel level, LivingEntity villager, ResourceLocation reactionId,
            ReactionContext context) {
        if (level == null || villager == null || reactionId == null || context == null) return false;
        Reaction reaction = ReactionRegistry.get(reactionId).orElse(null);
        if (reaction == null) return false;
        return fire(level, villager, reaction, context);
    }

    public static boolean fire(ServerLevel level, LivingEntity villager, Reaction reaction, ReactionContext context) {
        if (level == null || villager == null || reaction == null || context == null) return false;
        long gameTime = level.getGameTime();
        RandomSource random = level.getRandom();

        if (context.source() != ReactionContext.TriggerSource.COMMAND
                && ReactionLockTracker.isLocked(villager, gameTime)) {
            return false;
        }
        if (!ReactionCooldownTracker.tryClaim(villager, reaction.id(), reaction.cooldownTicks(), gameTime)) {
            return false;
        }
        if (reaction.chance() < 1.0F && random.nextFloat() >= reaction.chance()) {
            return false;
        }

        // Tag-based gating: Phase 5 wires ContextResolver. Until then, accept any
        // tags carried in the context (e.g., command-injected tags) and treat a
        // missing context_tags set as "no tags resolved yet" rather than rejecting.
        if (!context.contextTags().containsAll(reaction.conditions().requiredTags())) {
            return false;
        }

        String personalityKey = personalityKey(villager);
        List<ReactionBinding> candidates = new ArrayList<>(reaction.bindings().size());
        List<Double> weights = new ArrayList<>(reaction.bindings().size());
        for (ReactionBinding binding : reaction.bindings()) {
            if (!context.contextTags().containsAll(binding.requiredTags())) continue;
            float pm = binding.personalityMultiplier(personalityKey);
            double effective = (double) binding.weight() * pm;
            if (effective <= 0.0) continue;
            if (binding.chance() < 1.0F && random.nextFloat() >= binding.chance()) continue;
            candidates.add(binding);
            weights.add(effective);
        }
        if (candidates.isEmpty()) return false;

        Optional<ReactionBinding> picked = pickWeighted(candidates, weights, random);
        if (picked.isEmpty()) return false;
        ReactionBinding chosen = picked.get();

        Optional<ReactionBackend> backend = ReactionBackends.get(chosen.backendKey());
        if (backend.isEmpty()) {
            Townstead.LOGGER.debug("Reaction '{}' references unknown backend '{}'", reaction.id(), chosen.backendKey());
            return false;
        }

        boolean played = backend.get().play(level, villager, chosen.refIds(), chosen.args(), context);
        if (!played) return false;

        ReactionSideEffects.emit(level, villager, chosen.sound(), chosen.particles());
        int effectiveLock = computeLockTicks(reaction, chosen);
        if (effectiveLock > 0) {
            ReactionLockTracker.lock(villager, gameTime, effectiveLock);
        }
        // Mirror fan-out lands in Phase 5; the field exists but is intentionally
        // unused here so that adding MirrorPropagator later is a single hook.
        return true;
    }

    // ─────────────────────────── trigger event API ───────────────────────────

    /**
     * Invoked by the gesture event bridge after a player emote near a
     * villager has been resolved. Looks up matching {@code player_gesture}
     * triggers and fires their reactions on the villager.
     */
    public static int onPlayerGesture(ServerLevel level, Player player, LivingEntity villager, String emoteName) {
        if (villager == null || emoteName == null || emoteName.isBlank()) return 0;
        String key = emoteName.toLowerCase(Locale.ROOT);
        List<ResourceLocation> matches = ReactionRegistry.triggers().matchesFor(PlayerGestureTriggerType.KEY, key);
        if (matches.isEmpty()) return 0;
        ReactionContext ctx = new ReactionContext(ReactionContext.TriggerSource.GESTURE, player,
                villager.blockPosition(), Set.of(), 0);
        int fired = 0;
        for (ResourceLocation id : matches) if (fire(level, villager, id, ctx)) fired++;
        return fired;
    }

    /**
     * Invoked by a task lifecycle bridge after a task transitions through
     * a phase. {@code phase} is free-form (e.g. {@code start},
     * {@code transition:SELECT_RECIPE}, {@code stop:success}) and must
     * match the {@code phase} listed by the trigger.
     */
    public static int onTaskTransition(ServerLevel level, LivingEntity villager, ResourceLocation taskId, String phase) {
        if (taskId == null || phase == null || phase.isBlank()) return 0;
        String key = TaskTriggerType.composite(taskId.toString(), phase);
        List<ResourceLocation> matches = ReactionRegistry.triggers().matchesFor(TaskTriggerType.KEY, key);
        if (matches.isEmpty()) return 0;
        ReactionContext ctx = new ReactionContext(ReactionContext.TriggerSource.TASK, null,
                villager.blockPosition(), Set.of(), 0);
        int fired = 0;
        for (ResourceLocation id : matches) if (fire(level, villager, id, ctx)) fired++;
        return fired;
    }

    /**
     * Invoked by the context tick hook with the freshly resolved tag set
     * for a villager. Reactions are responsible for re-checking that all
     * their {@code required_tags} are present via the dispatcher's
     * binding-level gate; this method short-circuits when none of the
     * incoming tags index to any reaction.
     */
    public static int onContextEnter(ServerLevel level, LivingEntity villager, Set<String> newTags) {
        if (villager == null || newTags == null || newTags.isEmpty()) return 0;
        Set<ResourceLocation> seen = new HashSet<>();
        for (String tag : newTags) {
            String key = tag.toLowerCase(Locale.ROOT);
            for (ResourceLocation id : ReactionRegistry.triggers().matchesFor(ContextEnterTriggerType.KEY, key)) {
                seen.add(id);
            }
        }
        if (seen.isEmpty()) return 0;
        ReactionContext ctx = new ReactionContext(ReactionContext.TriggerSource.CONTEXT, null,
                villager.blockPosition(),
                Set.copyOf(newTags), 0);
        int fired = 0;
        for (ResourceLocation id : seen) if (fire(level, villager, id, ctx)) fired++;
        return fired;
    }

    /**
     * Invoked by {@code MirrorPropagator} on each villager near the
     * source villager. {@code depth} should be {@code 1}; further
     * propagation is blocked by the dispatcher to avoid recursion.
     */
    public static int onMirror(ServerLevel level, LivingEntity villager, ResourceLocation sourceReactionId,
            BlockPos location) {
        if (villager == null || sourceReactionId == null) return 0;
        List<ResourceLocation> matches = ReactionRegistry.triggers()
                .matchesFor(MirrorOfTriggerType.KEY, sourceReactionId.toString());
        if (matches.isEmpty()) return 0;
        ReactionContext ctx = new ReactionContext(ReactionContext.TriggerSource.MIRROR, null,
                location != null ? location : villager.blockPosition(), Set.of(), 1);
        int fired = 0;
        for (ResourceLocation id : matches) if (fire(level, villager, id, ctx)) fired++;
        return fired;
    }

    /**
     * Invoked when a villager dwells near a {@code townstead:idle_spot}
     * POI of the given spot type.
     */
    public static int onIdleSpot(ServerLevel level, LivingEntity villager, String spotId) {
        if (villager == null || spotId == null || spotId.isBlank()) return 0;
        List<ResourceLocation> matches = ReactionRegistry.triggers().matchesFor(IdleSpotTriggerType.KEY, spotId);
        if (matches.isEmpty()) return 0;
        ReactionContext ctx = new ReactionContext(ReactionContext.TriggerSource.IDLE_SPOT, null,
                villager.blockPosition(), Set.of(), 0);
        int fired = 0;
        for (ResourceLocation id : matches) if (fire(level, villager, id, ctx)) fired++;
        return fired;
    }

    /**
     * Invoked by the location tick hook on the stride for matching
     * {@code time} triggers (night, day, dawn, dusk). The hook is
     * responsible for honoring each trigger's {@code interval_ticks}.
     */
    public static int onTimePhase(ServerLevel level, LivingEntity villager, String phase) {
        if (villager == null || phase == null || phase.isBlank()) return 0;
        List<ResourceLocation> matches = ReactionRegistry.triggers()
                .matchesFor(TimeTriggerType.KEY, phase.toLowerCase(Locale.ROOT));
        if (matches.isEmpty()) return 0;
        ReactionContext ctx = new ReactionContext(ReactionContext.TriggerSource.TIME, null,
                villager.blockPosition(), Set.of(), 0);
        int fired = 0;
        for (ResourceLocation id : matches) if (fire(level, villager, id, ctx)) fired++;
        return fired;
    }

    // ──────────────────────────── internals ────────────────────────────

    /**
     * Pick the lock duration for the chosen binding. Preferred path:
     * compute from the binding's {@code shots} against the picked
     * Emotecraft ref's known duration. If the duration table doesn't
     * know the ref, fall back to the reaction's {@code lock_ticks}. If
     * both are zero/unknown, no lock is applied.
     */
    private static int computeLockTicks(Reaction reaction, ReactionBinding chosen) {
        if (EmotecraftReactionBackend.KEY.equals(chosen.backendKey())) {
            // Pick the first ref's duration as the representative; for
            // variant arrays they should be roughly equivalent in scale.
            String first = chosen.refIds().isEmpty() ? null : chosen.refIds().get(0);
            Optional<Integer> ticks = EmoteDurationIndex.ticksFor(first, chosen.shots());
            if (ticks.isPresent()) return ticks.get();
        }
        return reaction.lockTicks();
    }

    private static String personalityKey(LivingEntity entity) {
        if (entity instanceof VillagerEntityMCA mca) {
            try {
                Personality personality = mca.getVillagerBrain().getPersonality();
                if (personality != null) return personality.name().toLowerCase(Locale.ROOT);
            } catch (Throwable ignored) {}
        }
        return "default";
    }

    private static Optional<ReactionBinding> pickWeighted(List<ReactionBinding> entries, List<Double> weights,
            RandomSource random) {
        double total = 0.0;
        for (double w : weights) if (w > 0.0) total += w;
        if (total <= 0.0) return Optional.empty();
        double roll = random.nextDouble() * total;
        double accum = 0.0;
        for (int i = 0; i < entries.size(); i++) {
            double w = weights.get(i);
            if (w <= 0.0) continue;
            accum += w;
            if (roll < accum) return Optional.of(entries.get(i));
        }
        return Optional.of(entries.get(entries.size() - 1));
    }
}
