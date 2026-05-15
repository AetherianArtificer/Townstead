package com.aetherianartificer.townstead.reaction;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.reaction.backend.EmoteDurationIndex;
import com.aetherianartificer.townstead.reaction.backend.EmotecraftReactionBackend;
import com.aetherianartificer.townstead.reaction.backend.ReactionBackend;
import com.aetherianartificer.townstead.reaction.backend.ReactionBackends;
import com.aetherianartificer.townstead.reaction.effect.ReactionSideEffects;
import com.aetherianartificer.townstead.reaction.trigger.event.MirrorPropagator;
import com.aetherianartificer.townstead.reaction.trigger.types.ContextEnterTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.ContextPresentTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.GestureTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.IdleSpotTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.TaskTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.TimeTriggerType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Personality;
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

        if (context.source() != ReactionContext.TriggerSource.COMMAND) {
            if (ReactionLockTracker.isLocked(villager, gameTime)) return false;
            if (villager.isSleeping()) return false;
        }
        String reactionKey = reaction.id().toString();
        if (!ReactionCooldownTracker.canClaim(villager, reactionKey, reaction.cooldownTicks(), gameTime)) {
            return false;
        }
        if (reaction.chance() < 1.0F && random.nextFloat() >= reaction.chance()) {
            return false;
        }

        if (!context.contextTags().containsAll(reaction.conditions().requiredTags())) {
            return false;
        }

        String personalityKey = personalityKey(villager);
        List<ReactionBinding> candidates = new ArrayList<>(reaction.bindings().size());
        List<Double> weights = new ArrayList<>(reaction.bindings().size());
        for (ReactionBinding binding : reaction.bindings()) {
            if (!context.contextTags().containsAll(binding.requiredTags())) continue;
            // Filter by per-binding cooldown before personality so a binding
            // on cooldown is never picked.
            if (binding.cooldownTicks() > 0
                    && !ReactionCooldownTracker.canClaim(villager, bindingKey(reaction, binding),
                            binding.cooldownTicks(), gameTime)) {
                continue;
            }
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

        Optional<String> playedRef = backend.get().play(level, villager, chosen, context);
        if (playedRef.isEmpty()) return false;

        // Commit both cooldown stamps now that the fire is real.
        if (reaction.cooldownTicks() > 0) {
            ReactionCooldownTracker.claim(villager, reactionKey, gameTime);
        }
        if (chosen.cooldownTicks() > 0) {
            ReactionCooldownTracker.claim(villager, bindingKey(reaction, chosen), gameTime);
        }

        ReactionSideEffects.emit(level, villager, chosen.sound(), chosen.particles());
        // allow_movement bindings skip the lock entirely so the villager
        // can keep walking while the animation plays on top.
        if (!chosen.allowMovement()) {
            int effectiveLock = computeLockTicks(reaction, chosen);
            if (effectiveLock > 0) {
                ReactionLockTracker.lock(villager, gameTime, effectiveLock, reaction.id());
            }
        }
        applyHeartsAdjustment(villager, reaction, context, gameTime);
        MirrorPropagator.propagate(level, villager, reaction, playedRef.get(), context);
        return true;
    }

    /**
     * Adjust MCA hearts between the villager and the player who caused
     * this reaction, capped to once per MC day per (villager, player,
     * reaction). No-op when the reaction has {@code hearts: 0} or the
     * trigger source carries no player (context-driven reactions, etc.).
     */
    private static void applyHeartsAdjustment(LivingEntity villager, Reaction reaction, ReactionContext context,
            long gameTime) {
        if (reaction.hearts() == 0) return;
        if (!(context.playerCause() instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        if (!(villager instanceof VillagerEntityMCA mca)) return;
        String key = "hearts:" + sp.getUUID() + ":" + reaction.id();
        if (!ReactionCooldownTracker.canClaim(villager, key, HEARTS_DAILY_CAP_TICKS, gameTime)) return;
        ReactionCooldownTracker.claim(villager, key, gameTime);
        try {
            mca.getVillagerBrain().rewardHearts(sp, reaction.hearts());
        } catch (Throwable t) {
            Townstead.LOGGER.debug("Hearts adjustment for reaction '{}' failed: {}", reaction.id(), t.getMessage());
        }
    }

    /** One full Minecraft day in ticks — the heart-change cap window. */
    private static final int HEARTS_DAILY_CAP_TICKS = 24000;

    /**
     * Composite key for per-binding cooldown bookkeeping. Stable across
     * reload because it's derived from the binding's content (backend +
     * joined ref list), not its position in the bindings array.
     */
    private static String bindingKey(Reaction reaction, ReactionBinding binding) {
        return reaction.id() + "@" + binding.backendKey() + "/" + String.join(",", binding.refIds());
    }

    // ─────────────────────────── trigger event API ───────────────────────────

    /**
     * Invoked whenever an emote gesture happens near a villager: either
     * from a player running {@code /emote} (depth 0, with the player
     * causing it) or from another villager's reaction mirroring to its
     * neighbors (depth 1, no player). Depth-1 events do not re-mirror.
     */
    public static int onGesture(ServerLevel level, Player playerCause, LivingEntity villager, String emoteName,
            int depth) {
        if (villager == null || emoteName == null || emoteName.isBlank()) return 0;
        String key = emoteName.toLowerCase(Locale.ROOT);
        List<ResourceLocation> matches = ReactionRegistry.triggers().matchesFor(GestureTriggerType.KEY, key);
        if (matches.isEmpty()) return 0;
        ReactionContext ctx = new ReactionContext(ReactionContext.TriggerSource.GESTURE, playerCause,
                villager.blockPosition(), Set.of(), Math.max(0, depth));
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
     * Invoked by the context tick hook on every stride with the full
     * current tag set. Reactions whose {@code context_present} trigger
     * lists any of these tags fire (subject to the dispatcher's
     * cooldown, lock, and required-tag gates). Use this for "while X is
     * true" reactions like dancing while music plays.
     */
    public static int onContextPresent(ServerLevel level, LivingEntity villager, Set<String> currentTags) {
        if (villager == null || currentTags == null || currentTags.isEmpty()) return 0;
        Set<ResourceLocation> seen = new HashSet<>();
        for (String tag : currentTags) {
            String key = tag.toLowerCase(Locale.ROOT);
            for (ResourceLocation id : ReactionRegistry.triggers().matchesFor(ContextPresentTriggerType.KEY, key)) {
                seen.add(id);
            }
        }
        if (seen.isEmpty()) return 0;
        ReactionContext ctx = new ReactionContext(ReactionContext.TriggerSource.CONTEXT, null,
                villager.blockPosition(), Set.copyOf(currentTags), 0);
        int fired = 0;
        for (ResourceLocation id : seen) if (fire(level, villager, id, ctx)) fired++;
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
