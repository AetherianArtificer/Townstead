package com.aetherianartificer.townstead.reaction;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat;
import com.aetherianartificer.townstead.reaction.backend.EmoteDurationIndex;
import com.aetherianartificer.townstead.reaction.backend.EmotecraftReactionBackend;
import com.aetherianartificer.townstead.reaction.backend.ReactionBackend;
import com.aetherianartificer.townstead.reaction.backend.ReactionBackends;
import com.aetherianartificer.townstead.reaction.effect.ReactionSideEffects;
import com.aetherianartificer.townstead.reaction.trigger.event.MirrorPropagator;
import com.aetherianartificer.townstead.reaction.trigger.event.SocialInteractionTracker;
import com.aetherianartificer.townstead.reaction.trigger.types.ContextEnterTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.ContextExitTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.ContextPresentTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.DamageTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.GestureTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.IdleSpotTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.TaskTriggerType;
import com.aetherianartificer.townstead.reaction.trigger.types.TimeTriggerType;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;

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
 * the dispatcher gates by cooldown/lock/chance, selects a personality-weighted
 * outcome, then runs its optional legacy animation and composable Pheno outputs.
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
        boolean forced = context.source() == ReactionContext.TriggerSource.COMMAND;

        if (!forced) {
            if (ReactionLockTracker.isLocked(villager, gameTime)) return false;
            if (villager.isSleeping()) return false;
        }
        String reactionKey = reaction.id().toString();
        if (!forced && !ReactionCooldownTracker.canClaim(villager, reactionKey, reaction.cooldownTicks(), gameTime)) {
            return false;
        }
        if (!forced && reaction.chance() < 1.0F && random.nextFloat() >= reaction.chance()) {
            return false;
        }

        if (!forced && !context.contextTags().containsAll(reaction.conditions().requiredTags())) {
            return false;
        }
        if (!forced && reaction.phenoCondition().isPresent()
                && !reaction.phenoCondition().get().test(new ConditionContext(villager, context.counterpart()))) {
            return false;
        }

        String personalityKey = personalityKey(villager);
        List<ReactionBinding> candidates = new ArrayList<>(reaction.bindings().size());
        List<Double> weights = new ArrayList<>(reaction.bindings().size());
        for (ReactionBinding binding : reaction.bindings()) {
            if (!forced && !context.contextTags().containsAll(binding.requiredTags())) continue;
            if (!forced && binding.phenoCondition().isPresent()
                    && !binding.phenoCondition().get().test(new ConditionContext(villager, context.counterpart()))) continue;
            // Filter by per-binding cooldown before personality so a binding
            // on cooldown is never picked.
            if (!forced && binding.cooldownTicks() > 0
                    && !ReactionCooldownTracker.canClaim(villager, bindingKey(reaction, binding),
                            binding.cooldownTicks(), gameTime)) {
                continue;
            }
            float pm = binding.personalityMultiplier(personalityKey);
            double effective = (double) binding.weight() * pm;
            if (effective <= 0.0) continue;
            if (!forced && binding.chance() < 1.0F && random.nextFloat() >= binding.chance()) continue;
            candidates.add(binding);
            weights.add(effective);
        }
        if (candidates.isEmpty()) return false;

        Optional<ReactionBinding> picked = pickWeighted(candidates, weights, random);
        if (picked.isEmpty()) return false;
        ReactionBinding chosen = picked.get();

        Optional<String> playedRef = Optional.empty();
        if (chosen.hasAnimation()) {
            Optional<ReactionBackend> backend = ReactionBackends.get(chosen.backendKey());
            if (backend.isEmpty()) {
                Townstead.LOGGER.debug("Reaction '{}' references unavailable backend '{}'",
                        reaction.id(), chosen.backendKey());
            } else {
                playedRef = backend.get().play(level, villager, chosen, context);
            }
            if (chosen.animationRequired() && playedRef.isEmpty()) return false;
        }

        LivingEntity counterpart = context.counterpart();
        boolean producedOutput = playedRef.isPresent()
                || chosen.sound().isPresent() || chosen.particles().isPresent()
                || chosen.speechPool().filter(value -> !value.isBlank()).isPresent();
        if (chosen.phenoAction().isPresent()) {
            ActionContext actionContext = new ActionContext(villager, counterpart);
            chosen.phenoAction().get().run(actionContext);
            // An outcome was genuinely selected even when an individual action reports a soft
            // refusal (for example an expression display throttle). Claim the reaction cooldown
            // so the trigger cannot hammer the other actions every context stride.
            producedOutput = true;
        }
        if (reaction.phenoAction().isPresent()) {
            ActionContext actionContext = new ActionContext(villager, counterpart);
            reaction.phenoAction().get().run(actionContext);
            producedOutput = true;
        }
        if (!producedOutput) return false;

        // Commit both cooldown stamps now that the fire is real.
        if (!forced && reaction.cooldownTicks() > 0) {
            ReactionCooldownTracker.claim(villager, reactionKey, gameTime);
        }
        if (!forced && chosen.cooldownTicks() > 0) {
            ReactionCooldownTracker.claim(villager, bindingKey(reaction, chosen), gameTime);
        }

        ReactionSideEffects.emit(level, villager, chosen.sound(), chosen.particles());
        if (chosen.speechPool().isPresent() && villager instanceof VillagerEntityMCA mca) {
            String pool = chosen.speechPool().get().trim();
            if (!pool.isEmpty()) mca.sendChatToAllAround(pool);
        }
        // allow_movement bindings skip the lock entirely so the villager
        // can keep walking while the animation plays on top.
        if (!chosen.allowMovement()) {
            int effectiveLock = computeLockTicks(reaction, chosen);
            if (effectiveLock > 0) {
                ReactionLockTracker.lock(villager, gameTime, effectiveLock, reaction.id());
            }
        }
        applyHeartsAdjustment(villager, reaction, context, gameTime);
        playedRef.ifPresent(ref -> MirrorPropagator.propagate(level, villager, reaction, ref, context));
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
            SocialInteractionTracker.markHeartChange(villager, reaction.hearts(), gameTime);
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
    public static int onGesture(ServerLevel level, Entity gestureSource, LivingEntity villager, String emoteName,
            int depth) {
        if (villager == null || emoteName == null || emoteName.isBlank()) return 0;
        String key = GestureTriggerType.normalizeEmoteName(emoteName);
        List<ResourceLocation> matches = ReactionRegistry.triggers().matchesFor(GestureTriggerType.KEY, key);
        if (matches.isEmpty()) return 0;
        Player playerCause = gestureSource instanceof Player player ? player : null;
        ReactionContext ctx = new ReactionContext(ReactionContext.TriggerSource.GESTURE, playerCause,
                villager.blockPosition(), Set.of(), Math.max(0, depth), gestureSource instanceof LivingEntity living ? living : null);
        int fired = 0;
        for (ResourceLocation id : matches) {
            Reaction reaction = ReactionRegistry.get(id).orElse(null);
            if (reaction != null && gestureTriggerMatches(reaction, key, gestureSource, villager)
                    && fire(level, villager, reaction, ctx)) fired++;
        }
        return fired;
    }

    /** Largest authored range for a gesture, used to bound the initial entity query. */
    public static double gestureRange(String emoteName) {
        if (emoteName == null || emoteName.isBlank()) return 0.0;
        String key = GestureTriggerType.normalizeEmoteName(emoteName);
        double range = 0.0;
        for (ResourceLocation id : ReactionRegistry.triggers().matchesFor(GestureTriggerType.KEY, key)) {
            Reaction reaction = ReactionRegistry.get(id).orElse(null);
            if (reaction == null) continue;
            for (JsonObject raw : reaction.rawTriggers()) {
                if (!GestureTriggerType.KEY.equals(GsonHelper.getAsString(raw, "type", ""))) continue;
                GestureTriggerType.Instance trigger = (GestureTriggerType.Instance) new GestureTriggerType().parse(raw);
                if (trigger != null && trigger.emoteName().equals(key)) {
                    range = Math.max(range, trigger.maxDistance());
                }
            }
        }
        return range;
    }

    private static boolean gestureTriggerMatches(Reaction reaction, String emoteKey, Entity source,
                                                 LivingEntity villager) {
        for (JsonObject raw : reaction.rawTriggers()) {
            if (!GestureTriggerType.KEY.equals(GsonHelper.getAsString(raw, "type", ""))) continue;
            GestureTriggerType.Instance trigger = (GestureTriggerType.Instance) new GestureTriggerType().parse(raw);
            if (trigger == null || !trigger.emoteName().equals(emoteKey)) continue;
            if (source == null) return true;
            if (source.distanceToSqr(villager) > trigger.maxDistance() * trigger.maxDistance()) continue;
            if (trigger.minDot() <= -1.0F) return true;
            var towardSource = source.position().subtract(villager.position());
            if (towardSource.lengthSqr() < 1.0E-6) return true;
            double dot = villager.getLookAngle().normalize().dot(towardSource.normalize());
            if (dot >= trigger.minDot()) return true;
        }
        return false;
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
        return onContextEnter(level, villager, newTags, newTags);
    }

    /**
     * Dispatch newly-entered trigger keys while evaluating outcome requirements against the full
     * current snapshot. This lets "entered shelter while it is raining" work as authored: only
     * {@code under_roof} needs to be new, while {@code raining} may already have been present.
     */
    public static int onContextEnter(ServerLevel level, LivingEntity villager, Set<String> newTags,
                                     Set<String> currentTags) {
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
                Set.copyOf(currentTags == null ? newTags : currentTags), 0);
        int fired = 0;
        for (ResourceLocation id : seen) {
            Reaction reaction = ReactionRegistry.get(id).orElse(null);
            if (reaction != null && contextTriggerMatches(reaction, ContextEnterTriggerType.KEY,
                    newTags, ctx.contextTags()) && fire(level, villager, reaction, ctx)) fired++;
        }
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
        for (ResourceLocation id : seen) {
            Reaction reaction = ReactionRegistry.get(id).orElse(null);
            if (reaction != null && contextTriggerMatches(reaction, ContextPresentTriggerType.KEY,
                    currentTags, currentTags) && fire(level, villager, reaction, ctx)) fired++;
        }
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
     * also honors each trigger's {@code interval_ticks}, staggered by entity.
     */
    public static int onTimePhase(ServerLevel level, LivingEntity villager, String phase) {
        if (villager == null || phase == null || phase.isBlank()) return 0;
        List<ResourceLocation> matches = ReactionRegistry.triggers()
                .matchesFor(TimeTriggerType.KEY, phase.toLowerCase(Locale.ROOT));
        if (matches.isEmpty()) return 0;
        ReactionContext ctx = new ReactionContext(ReactionContext.TriggerSource.TIME, null,
                villager.blockPosition(), Set.of(), 0);
        int fired = 0;
        for (ResourceLocation id : matches) {
            Reaction reaction = ReactionRegistry.get(id).orElse(null);
            if (reaction != null && timeTriggerDue(reaction, phase, level.getGameTime(), villager.getId())
                    && fire(level, villager, reaction, ctx)) fired++;
        }
        return fired;
    }

    /** Dispatches once when a previously satisfied context set stops being satisfied. */
    public static int onContextExit(ServerLevel level, LivingEntity villager, Set<String> exitedTags,
                                    Set<String> priorTags, Set<String> currentTags) {
        if (villager == null || exitedTags == null || exitedTags.isEmpty()) return 0;
        Set<ResourceLocation> seen = new HashSet<>();
        for (String tag : exitedTags) {
            String key = tag.toLowerCase(Locale.ROOT);
            seen.addAll(ReactionRegistry.triggers().matchesFor(ContextExitTriggerType.KEY, key));
        }
        if (seen.isEmpty()) return 0;
        Set<String> before = priorTags == null ? Set.of() : Set.copyOf(priorTags);
        Set<String> after = currentTags == null ? Set.of() : Set.copyOf(currentTags);
        ReactionContext context = new ReactionContext(ReactionContext.TriggerSource.CONTEXT, null,
                villager.blockPosition(), after, 0);
        int fired = 0;
        for (ResourceLocation id : seen) {
            Reaction reaction = ReactionRegistry.get(id).orElse(null);
            if (reaction != null && contextExitTriggerMatches(reaction, exitedTags, before)
                    && fire(level, villager, reaction, context)) fired++;
        }
        return fired;
    }

    /**
     * Dispatches one real damage event from the victim, attacker, and nearby-witness perspectives.
     * Witness scanning is skipped unless a loaded reaction asks for that role.
     */
    public static int onDamage(ServerLevel level, LivingEntity victim, DamageSource source, float amount) {
        if (level == null || victim == null || source == null || !Float.isFinite(amount) || amount <= 0) return 0;
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        int fired = 0;
        if (victim instanceof VillagerEntityMCA villager) {
            fired += dispatchDamageRole(level, villager, attacker, source, amount, "victim");
        }
        if (attacker instanceof VillagerEntityMCA villager) {
            fired += dispatchDamageRole(level, villager, victim, source, amount, "attacker");
        }
        if (!ReactionRegistry.triggers().matchesFor(DamageTriggerType.KEY, "witness").isEmpty()) {
            AABB area = victim.getBoundingBox().inflate(12.0);
            for (VillagerEntityMCA witness : level.getEntitiesOfClass(VillagerEntityMCA.class, area,
                    entity -> entity != victim && entity != attacker && entity.isAlive())) {
                fired += dispatchDamageRole(level, witness, victim, source, amount, "witness");
            }
        }
        return fired;
    }

    private static int dispatchDamageRole(ServerLevel level, VillagerEntityMCA actor, LivingEntity counterpart,
                                          DamageSource source, float amount, String role) {
        List<ResourceLocation> matches = ReactionRegistry.triggers().matchesFor(DamageTriggerType.KEY, role);
        if (matches.isEmpty()) return 0;
        Set<String> tags = new HashSet<>(
                com.aetherianartificer.townstead.reaction.trigger.event.ContextResolver.tagsFor(level, actor));
        tags.add("damage");
        tags.add("damage_role:" + role);
        String sourceKey = source.getMsgId().toLowerCase(Locale.ROOT);
        tags.add("damage_source:" + sourceKey);
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) tags.add("damage_fire");
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) tags.add("damage_explosion");
        Entity causing = source.getEntity();
        if (causing instanceof Player) tags.add("attacker_player");
        if (causing instanceof VillagerEntityMCA) tags.add("attacker_villager");
        if (causing instanceof net.minecraft.world.entity.monster.Monster) tags.add("attacker_monster");
        if (causing instanceof Player || causing instanceof VillagerEntityMCA) tags.add("attacker_person");
        LivingEntity harmed = "victim".equals(role) ? actor : counterpart;
        if (harmed instanceof Player) tags.add("victim_player");
        if (harmed instanceof VillagerEntityMCA) tags.add("victim_villager");
        if (harmed instanceof net.minecraft.world.entity.monster.Monster) tags.add("victim_monster");
        if (harmed instanceof Player || harmed instanceof VillagerEntityMCA) tags.add("victim_person");
        Player playerCause = causing instanceof Player player ? player : null;
        ReactionContext context = new ReactionContext(ReactionContext.TriggerSource.DAMAGE, playerCause,
                victimLocation(role, actor, counterpart), Set.copyOf(tags), 0, counterpart);
        int fired = 0;
        for (ResourceLocation id : matches) {
            Reaction reaction = ReactionRegistry.get(id).orElse(null);
            if (reaction != null && damageTriggerMatches(reaction, role, sourceKey, amount)
                    && fire(level, actor, reaction, context)) fired++;
        }
        return fired;
    }

    private static net.minecraft.core.BlockPos victimLocation(String role, LivingEntity actor,
                                                               LivingEntity counterpart) {
        return "victim".equals(role) || counterpart == null
                ? actor.blockPosition() : counterpart.blockPosition();
    }

    // ──────────────────────────── internals ────────────────────────────

    private static boolean contextTriggerMatches(Reaction reaction, String type, Set<String> entered,
                                                 Set<String> current) {
        for (JsonObject trigger : reaction.rawTriggers()) {
            if (!type.equals(GsonHelper.getAsString(trigger, "type", ""))) continue;
            List<String> required = ReactionConditions.parseStringArray(trigger, "tags").stream()
                    .map(value -> value.toLowerCase(Locale.ROOT)).toList();
            if (required.isEmpty() || !current.containsAll(required)) continue;
            if (!ContextEnterTriggerType.KEY.equals(type)) return true;
            for (String value : required) if (entered.contains(value)) return true;
        }
        return false;
    }

    private static boolean contextExitTriggerMatches(Reaction reaction, Set<String> exited,
                                                     Set<String> prior) {
        for (JsonObject trigger : reaction.rawTriggers()) {
            if (!ContextExitTriggerType.KEY.equals(GsonHelper.getAsString(trigger, "type", ""))) continue;
            List<String> required = ReactionConditions.parseStringArray(trigger, "tags").stream()
                    .map(value -> value.toLowerCase(Locale.ROOT)).toList();
            if (required.isEmpty() || !prior.containsAll(required)) continue;
            for (String value : required) if (exited.contains(value)) return true;
        }
        return false;
    }

    private static boolean damageTriggerMatches(Reaction reaction, String role, String source, float amount) {
        for (JsonObject trigger : reaction.rawTriggers()) {
            if (!DamageTriggerType.KEY.equals(GsonHelper.getAsString(trigger, "type", ""))) continue;
            if (!role.equalsIgnoreCase(GsonHelper.getAsString(trigger, "role", "victim"))) continue;
            if (amount < GsonHelper.getAsFloat(trigger, "min_amount", 0)) continue;
            List<String> sources = ReactionConditions.parseStringArray(trigger, "sources").stream()
                    .map(value -> value.toLowerCase(Locale.ROOT)).toList();
            if (sources.isEmpty() || sources.contains(source)) return true;
        }
        return false;
    }

    private static boolean timeTriggerDue(Reaction reaction, String phase, long gameTime, int entityId) {
        for (JsonObject trigger : reaction.rawTriggers()) {
            if (!TimeTriggerType.KEY.equals(GsonHelper.getAsString(trigger, "type", ""))) continue;
            if (!phase.equalsIgnoreCase(GsonHelper.getAsString(trigger, "phase", ""))) continue;
            int interval = Math.max(20, GsonHelper.getAsInt(trigger, "interval_ticks", 1200));
            if (Math.floorMod(gameTime + (entityId & 0x0F), interval) < 20) return true;
        }
        return false;
    }

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
                if (personality != null) return McaPersonalityCompat.legacyName(personality).toLowerCase(Locale.ROOT);
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
