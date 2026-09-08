package com.aetherianartificer.townstead.dialogue.conversation;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.dialogue.contextual.*;
import com.aetherianartificer.townstead.hangout.*;
import com.aetherianartificer.townstead.expression.ExpressionService;
import com.aetherianartificer.townstead.performance.*;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.reaction.ReactionLockTracker;
import com.aetherianartificer.townstead.reaction.trigger.event.DialogueStateTracker;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.aetherianartificer.townstead.social.RelationshipLedger;
import com.aetherianartificer.townstead.social.RelationshipQualities;
import com.aetherianartificer.townstead.social.RelationshipService;

/** Server-owned social simulation. Idle proximity starts exchanges without a venue or activity. */
public final class ConversationEngine {
    private static final String CHANNEL = "conversation";
    private static final Map<ServerLevel, Runtime> LEVELS = new IdentityHashMap<>();
    private static final DialogueSelector LINES = new DialogueSelector();
    private static final class Runtime {
        final Map<UUID, Live> members = new LinkedHashMap<>();
        final Map<UUID, Long> cooldowns = new HashMap<>();
        final Deque<Utterance> audible = new ArrayDeque<>();
    }
    private record Utterance(Vec3 position, long until) {}
    private static final class Live {
        final ConversationSession session;
        final boolean preview;
        final Random random;
        final List<PerformanceHandle> handles = new ArrayList<>();
        Live(ConversationSession session, boolean preview, Random random) {
            this.session = session; this.preview = preview; this.random = random;
        }
        void stopGestures() { handles.forEach(PerformanceHandle::stop); handles.clear(); }
    }
    private ConversationEngine() {}

    public static boolean active(UUID villager) {
        return LEVELS.values().stream().anyMatch(runtime -> runtime.members.containsKey(villager));
    }
    public static void clear() {
        LEVELS.values().forEach(runtime -> new HashSet<>(runtime.members.values()).forEach(Live::stopGestures));
        LEVELS.clear(); LINES.clear();
    }
    public static void forget(VillagerEntityMCA actor) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        Runtime runtime = LEVELS.get(level); if (runtime == null) return;
        Live live = runtime.members.get(actor.getUUID());
        if (live != null) { live.session.cancel("participant_removed"); end(level, runtime, live); }
    }

    /** Discovery is staggered and deliberately does not take ownership of navigation or work. */
    public static void consider(VillagerEntityMCA actor) {
        if (!TownsteadConfig.ENABLE_CONVERSATIONS.get()
                || !(actor.level() instanceof ServerLevel level) || ConversationTopics.all().isEmpty()) return;
        long now = level.getGameTime();
        if (Math.floorMod(now + actor.getUUID().hashCode(), 200) != 0 || !available(actor)) return;
        Runtime runtime = LEVELS.computeIfAbsent(level, ignored -> new Runtime());
        if (runtime.members.containsKey(actor.getUUID()) || runtime.cooldowns.getOrDefault(actor.getUUID(), 0L) > now) return;
        if (actor.getRandom().nextDouble() >= TownsteadConfig.IDLE_CONVERSATION_CHANCE.get()) return;
        List<VillagerEntityMCA> nearby = level.getEntitiesOfClass(VillagerEntityMCA.class,
                actor.getBoundingBox().inflate(6), other -> other != actor && available(other)
                        && !runtime.members.containsKey(other.getUUID()) && actor.distanceToSqr(other) <= 36
                        && actor.hasLineOfSight(other));
        // Familiar companions are attractive, but an unfamiliar neighbor retains a chance.
        ConversationMemory memory = ConversationSavedData.get(level.getServer()).memory();
        ChronicleSavedData social = socialData(level.getServer());
        long today = TownsteadCalendar.worldDay(level.getServer());
        Random random = new Random(actor.getRandom().nextLong());
        VillagerEntityMCA other = ConversationSession.choose(nearby, candidate -> {
            ConversationMemory.View history = memory.view(actor.getUUID(), candidate.getUUID(), now);
            if (history.lastAt() != Long.MIN_VALUE && now - history.lastAt() < 1200) return 0;
            if (runtime.cooldowns.getOrDefault(candidate.getUUID(), 0L) > now) return 0;
            return Math.max(0.2, 1 + social.relationships().value(actor.getUUID(), candidate.getUUID(),
                    RelationshipQualities.AFFECTION, today) / 20D);
        }, random);
        if (other != null) request(actor, other, null, false);
    }

    /** Shared entry point for idle AI, Pheno, hangouts and operator previews. */
    public static boolean request(VillagerEntityMCA actor, VillagerEntityMCA other,
                                  ResourceLocation requestedTopic, boolean preview) {
        if (!preview && !TownsteadConfig.ENABLE_CONVERSATIONS.get()) return false;
        if (!(actor.level() instanceof ServerLevel level) || other.level() != level || actor == other
                || !available(actor) || !available(other) || actor.distanceToSqr(other) > 36
                || !actor.hasLineOfSight(other) || !other.hasLineOfSight(actor)) return false;
        Runtime runtime = LEVELS.computeIfAbsent(level, ignored -> new Runtime());
        long now = level.getGameTime();
        if (runtime.members.containsKey(actor.getUUID()) || runtime.members.containsKey(other.getUUID())) return false;
        ConversationMemory.View history = ConversationSavedData.get(level.getServer()).memory().view(actor.getUUID(), other.getUUID(), now);
        ChronicleSavedData social = socialData(level.getServer());
        if (!preview && (runtime.cooldowns.getOrDefault(actor.getUUID(), 0L) > now
                || runtime.cooldowns.getOrDefault(other.getUUID(), 0L) > now
                || history.lastAt() != Long.MIN_VALUE && now - history.lastAt() < 1200)) return false;
        Random random = new Random(actor.getRandom().nextLong());
        List<ConversationTopic> topics = ConversationTopics.all().values().stream()
                .filter(topic -> requestedTopic == null || topic.id().equals(requestedTopic)).toList();
        if (topics.isEmpty()) return false;
        if (!preview) {
            long today=TownsteadCalendar.worldDay(level.getServer());
            com.aetherianartificer.townstead.social.InitialImpressions.apply(social,actor,other,today);
            com.aetherianartificer.townstead.social.InitialImpressions.apply(social,other,actor,today);
        }
        ConversationTopic topic = ConversationSession.choose(topics, candidate -> {
            double gate = gate(candidate.gate(), actor, other);
            ConversationTopic.Turn opener = candidate.turns().get(candidate.start());
            VillagerEntityMCA speaker = opener.speaker() == ConversationTopic.Speaker.INITIATOR ? actor : other;
            if (gate(opener.gate(), speaker, speaker == actor ? other : actor) <= 0) return 0;
            long repeats = history.topics().stream().filter(candidate.id().toString()::equals).count();
            // Stable individual interests emerge without assigning every member of a Root the same tastes.
            double interest = 0.65 + Math.floorMod(Objects.hash(actor.getUUID(), candidate.id()), 101) / 100D;
            return candidate.weight() * gate * interest / (1 + repeats * 3);
        }, random);
        if (topic == null) return false;
        Live live = new Live(new ConversationSession(topic, actor.getUUID(), other.getUUID(), now), preview, random);
        runtime.members.put(actor.getUUID(), live); runtime.members.put(other.getUUID(), live);
        return true;
    }

    public static void tick(MinecraftServer server) {
        for (var entry : new ArrayList<>(LEVELS.entrySet())) {
            ServerLevel level = entry.getKey(); if (level.getServer() != server) continue;
            Runtime runtime = entry.getValue(); long now = level.getGameTime();
            runtime.cooldowns.entrySet().removeIf(e -> e.getValue() <= now);
            runtime.audible.removeIf(e -> e.until() <= now);
            if (now % 10 != 0) continue;
            for (Live live : new LinkedHashSet<>(runtime.members.values())) {
                ConversationSession session = live.session;
                VillagerEntityMCA a = find(level, session.initiator()), b = find(level, session.responder());
                boolean valid = (live.preview || TownsteadConfig.ENABLE_CONVERSATIONS.get())
                        && a != null && b != null && available(a) && available(b)
                        && a.distanceToSqr(b) <= 49 && a.hasLineOfSight(b) && b.hasLineOfSight(a);
                session.advance(now, valid, turn -> {
                    VillagerEntityMCA speaker = turn.speaker() == ConversationTopic.Speaker.INITIATOR ? a : b;
                    return gate(turn.gate(), speaker, speaker == a ? b : a);
                }, live.random);
                if (session.status() == ConversationSession.Status.COMPLETE || session.status() == ConversationSession.Status.CANCELLED) {
                    end(level, runtime, live); continue;
                }
                if (!valid) continue;
                VillagerEntityMCA speaker = session.speaker().equals(a.getUUID()) ? a : b;
                VillagerEntityMCA listener = speaker == a ? b : a;
                speaker.getLookControl().setLookAt(listener, 25F, 25F);
                listener.getLookControl().setLookAt(speaker, 25F, 25F);
                if (!session.due(now) || runtime.audible.stream().anyMatch(u -> u.position().distanceToSqr(speaker.position()) < 144)) continue;
                if (gate(session.turn().gate(), speaker, listener) <= 0) {
                    session.cancel("turn_context_changed"); end(level, runtime, live); continue;
                }
                ConversationTopic.Turn turn = session.turn();
                String poolId = session.topic().id() + "/" + turn.id();
                List<DialoguePalette.Line> lines = new ArrayList<>();
                for (String translation : turn.lines()) lines.add(new DialoguePalette.Line(translation, translation, 1, Set.of(), Set.of(), Set.of()));
                DialoguePalette palette = new DialoguePalette(session.topic().id(), poolId, 0, 16, lines);
                var selected = LINES.select(speaker.getUUID(), new DialogueRequest(poolId, Set.of(), Set.of(), Set.of()),
                        List.of(palette), now, live.random);
                if (selected.isEmpty()) { session.cancel("no_line"); end(level, runtime, live); continue; }
                speaker.sendChatToAllAround(selected.get().translation());
                runtime.audible.addLast(new Utterance(speaker.position(), now + Math.max(40, turn.duration() - 10)));
                live.stopGestures(); present(level, live, speaker, listener, turn.gesture()); present(level, live, listener, speaker, turn.listener());
                session.spoken(now);
            }
        }
    }

    private static void present(ServerLevel level, Live live, VillagerEntityMCA actor, VillagerEntityMCA other, ConversationTopic.Cue cue) {
        if (cue.performance() != null) {
            PerformanceHandle handle = PerformanceProviders.play(level,
                    new PerformanceRequest(actor, cue.performance(), CHANNEL, 32, 45, PerformanceRequest.Fallback.NONE));
            if (handle != null) live.handles.add(handle);
        }
        if (cue.expression() != null) ExpressionService.emit(actor, cue.expression(), other);
    }
    private static double gate(ConversationTopic.Gate gate, VillagerEntityMCA actor, VillagerEntityMCA other) {
        if (actor == null || other == null) return 0;
        return gate.weight(context(actor, other), condition -> condition.test(new ConditionContext(actor, other)),
                value -> value.get(com.aetherianartificer.townstead.pheno.selector.SelectorContext.of(new ConditionContext(actor, other))));
    }
    public static DialogueRequest context(VillagerEntityMCA actor, VillagerEntityMCA other) {
        Set<String> facts = new LinkedHashSet<>(), relationships = new LinkedHashSet<>();
        if (actor.level() instanceof ServerLevel level) {
            long now = level.getGameTime();
            ConversationMemory.View history = ConversationSavedData.get(level.getServer()).memory().view(actor.getUUID(), other.getUUID(), now);
            ChronicleSavedData social = socialData(level.getServer());
            double affection = social.relationships().value(actor.getUUID(), other.getUUID(), RelationshipQualities.AFFECTION,
                    TownsteadCalendar.worldDay(level.getServer()));
            relationships.add(history.meetings() == 0 ? "stranger" : "familiar");
            if (affection >= 12 && history.meetings() >= 4) relationships.add("friend");
            if (affection <= -8) relationships.add("strained");
            relationships.addAll(com.aetherianartificer.townstead.social.RelationshipDescriptors.tags(actor, other));
            if (!history.memory().isEmpty()) facts.add("memory:" + history.memory());
            social.memoriesFor(actor.getUUID()).stream()
                    .filter(memory -> other.getUUID().equals(memory.otherParty()) && memory.strength() > 0)
                    .map(memory -> "memory:" + memory.memoryKey()).forEach(facts::add);
            if (affection <= -4) facts.add("relationship:strained");
        }
        int mood = actor.getVillagerBrain().getMoodValue(); facts.add(mood < 0 ? "mood:low" : "mood:content");
        if (com.aetherianartificer.townstead.profession.ProfessionIdentity.rawId(actor)
                .equals(com.aetherianartificer.townstead.profession.ProfessionIdentity.rawId(other))) facts.add("profession:shared");
        HangoutVisit visit = HangoutEngine.visit(actor.getUUID());
        if (visit != null && visit.phase() == HangoutVisit.Phase.PRESENT) {
            facts.add("venue:" + visit.venueDefinition());
            HangoutVenue venue = HangoutData.venues().get(visit.venueDefinition());
            if (venue != null) venue.tags().forEach(tag -> facts.add("venue_tag:" + tag));
        }
        return DialogueDirector.describe(actor, "conversation", facts, relationships, other);
    }
    public static boolean available(VillagerEntityMCA actor) {
        if (!actor.isAlive() || actor.isRemoved() || actor.isSleeping() || actor.getVillagerBrain().isPanicking()
                || actor.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || DialogueStateTracker.activePartner(actor) != null
                || com.aetherianartificer.townstead.root.LifeStageProgression.isBabyStage(actor)
                || ReactionLockTracker.isLocked(actor, actor.level().getGameTime())) return false;
        if (actor.getLastHurtByMob() != null && actor.tickCount - actor.getLastHurtByMobTimestamp() < 200) return false;
        var needs = com.aetherianartificer.townstead.villager.TownsteadVillagers.get(actor).needs();
        if (needs.hunger() <= com.aetherianartificer.townstead.hunger.HungerData.EMERGENCY_THRESHOLD
                || needs.thirst() <= com.aetherianartificer.townstead.thirst.ThirstData.EMERGENCY_THRESHOLD
                || needs.fatigue() >= com.aetherianartificer.townstead.fatigue.FatigueData.EXHAUSTED_THRESHOLD) return false;
        HangoutVisit visit = HangoutEngine.visit(actor.getUUID());
        if (visit != null) return visit.phase() == HangoutVisit.Phase.PRESENT;
        Activity activity = actor.getBrain().getSchedule().getActivityAt((int) (actor.level().getDayTime() % 24000L));
        return !actor.isPassenger() && actor.getNavigation().isDone() && (activity == Activity.IDLE || activity == Activity.MEET);
    }
    private static VillagerEntityMCA find(ServerLevel level, UUID id) {
        return level.getEntity(id) instanceof VillagerEntityMCA actor ? actor : null;
    }
    private static void end(ServerLevel level, Runtime runtime, Live live) {
        ConversationSession session = live.session;
        if (!runtime.members.remove(session.initiator(), live)) return;
        runtime.members.remove(session.responder(), live); live.stopGestures();
        long now = level.getGameTime();
        runtime.cooldowns.put(session.initiator(), now + 400); runtime.cooldowns.put(session.responder(), now + 400);
        ConversationTopic.Outcome outcome = session.outcome();
        VillagerEntityMCA a = find(level, session.initiator()), b = find(level, session.responder());
        if (outcome != null && !live.preview && a != null && b != null) {
            ConversationSavedData data = ConversationSavedData.get(level.getServer());
            ConversationMemory.Completion completion = data.memory().completeDetailed(a.getUUID(), a.getName().getString(), b.getUUID(), b.getName().getString(),
                    session.topic().id().toString(), outcome, now, false);
            data.setDirty();
            if (completion.rewarded()) {
                ChronicleSavedData social = socialData(level.getServer());
                long today = TownsteadCalendar.worldDay(level.getServer());
                applyConversationRelationship(social, a.getUUID(), b.getUUID(), outcome.initiatorOpinion(), completion.operationId(), session.topic().id(), today);
                applyConversationRelationship(social, b.getUUID(), a.getUUID(), outcome.responderOpinion(), completion.operationId(), session.topic().id(), today);
                applyConversationRelationships(social, a.getUUID(), b.getUUID(), outcome.initiatorRelationship(), completion.operationId(), session.topic().id(), today);
                applyConversationRelationships(social, b.getUUID(), a.getUUID(), outcome.responderRelationship(), completion.operationId(), session.topic().id(), today);
                social.addEpisodicMemory(a.getUUID(), completion.operationId() + ":memory:initiator",
                        outcome.initiatorMemory(), b.getUUID(), today, session.topic().id().toString(),
                        Map.of("topic", session.topic().id().toString(), "other_name", b.getName().getString()));
                social.addEpisodicMemory(b.getUUID(), completion.operationId() + ":memory:responder",
                        outcome.responderMemory(), a.getUUID(), today, session.topic().id().toString(),
                        Map.of("topic", session.topic().id().toString(), "other_name", a.getName().getString()));
                RelationshipService.recognizeFriendship(social, a.getUUID(), a.getName().getString(), b.getUUID(),
                        b.getName().getString(), data.memory().view(a.getUUID(), b.getUUID(), now).meetings(), today);
                a.getVillagerBrain().modifyMoodValue(outcome.initiatorMood()); b.getVillagerBrain().modifyMoodValue(outcome.responderMood());
                com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps.conversation(a, b, session.topic().id(),
                        outcome.memory(), outcome.initiatorMemory(), outcome.responderMemory());
                com.aetherianartificer.townstead.api.impl.v1.ApiEvents.conversationHeld(a, b, session.topic().id(),
                        outcome.memory());
            }
        }
        if (TownsteadConfig.DEBUG_LOGGING.get()) Townstead.LOGGER.info("[Conversation] {} {} -> {}: {} {}",
                session.topic().id(), session.initiator(), session.responder(), session.status(), session.reason());
    }

    private static ChronicleSavedData socialData(MinecraftServer server) {
        return RelationshipService.data(server);
    }

    private static void applyConversationRelationship(ChronicleSavedData data, UUID from, UUID toward, int amount,
                                                      String operation, ResourceLocation topic, long today) {
        if (amount == 0) return;
        var definition = RelationshipQualities.byId(RelationshipQualities.AFFECTION);
        data.applyRelationship(from, toward, new RelationshipLedger.Contribution(operation,
                RelationshipQualities.AFFECTION, amount, today, definition.defaultHalfLifeDays(), topic.toString()));
    }
    private static void applyConversationRelationships(ChronicleSavedData data, UUID from, UUID toward,
                                                       List<ConversationTopic.RelationshipChange> changes,
                                                       String operation, ResourceLocation topic, long today) {
        for (ConversationTopic.RelationshipChange change : changes) {
            data.applyRelationship(from, toward, new RelationshipLedger.Contribution(operation,
                    change.quality(), change.amount(), today,
                    change.halfLifeDays() < 0 ? RelationshipQualities.byId(change.quality()).defaultHalfLifeDays() : change.halfLifeDays(),
                    topic.toString()));
        }
    }

    public static String describe(VillagerEntityMCA actor, VillagerEntityMCA other) {
        Runtime runtime = actor.level() instanceof ServerLevel level ? LEVELS.get(level) : null;
        Live live = runtime == null ? null : runtime.members.get(actor.getUUID());
        String current = live == null ? "none" : live.session.topic().id() + "/" + live.session.turn().id() + " " + live.session.status();
        if (!(actor.level() instanceof ServerLevel level)) return current;
        var history = ConversationSavedData.get(level.getServer()).memory().view(actor.getUUID(), other.getUUID(), level.getGameTime());
        double affection = socialData(level.getServer()).relationships().value(actor.getUUID(), other.getUUID(),
                RelationshipQualities.AFFECTION, TownsteadCalendar.worldDay(level.getServer()));
        return "conversation=" + current + ", available=" + available(actor) + ", affection=" + affection
                + ", meetings=" + history.meetings() + ", memory=" + history.memory() + ", recent=" + history.topics();
    }
}
