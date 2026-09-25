package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.aetherianartificer.townstead.dialogue.conversation.ConversationTopic;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * One encounter, walked step by step: opening, topics with their expansions and pauses, closing.
 * The runtime asks {@link #next()} for a step, tries to speak it, and reports back with
 * {@link #spoke} or {@link #failed()}. Pure logic; the world is reached through {@link Driver}.
 */
public final class EncounterRun {
    public enum Side { A, B; public Side other() { return this == A ? B : A; } }

    public static final ResourceLocation ANSWER = ResourceLocation.tryParse("townstead:answer");
    /** A line that only replies to an open pair: a laugh, sympathy, a yes or no to an invitation. */
    public static final ResourceLocation ACKNOWLEDGE = ResourceLocation.tryParse("townstead:acknowledge");
    static final int IDLE_LINE_CAP = 28, HANGOUT_LINE_CAP = 90, TOPIC_ATTEMPTS = 4;
    static final double QUICK_HELLO = 0.12;

    /** A topic chosen for a speaker, with its bound subject. */
    public record Binding(ConversationTopic topic, @Nullable LineComposer.Subject subject, @Nullable ResourceLocation subjectKind) {}

    public interface Driver {
        @Nullable Binding bindTopic(Side speaker, Set<ResourceLocation> usedTopics, @Nullable ResourceLocation previousKind);
        double replyWeight(ConversationTopic.Turn turn, Side speaker);
        double chattiness();
        /** How interested one side is at the start: personality, relationship and mood. Defaults to the pair's chattiness. */
        default double interest(Side side) { return chattiness(); }
        boolean greetedToday();
        /** The mood the pair starts in, from their relationship: warm, tense, or null for neither. */
        default @Nullable String mood() { return null; }
        /** The side whose visit is ending, or null while both stay. Only hangouts leave. */
        @Nullable Side leaving();
        @Nullable ConversationMove move(ResourceLocation id);
        Random random();
    }

    public sealed interface Step permits Speak, Pause, End {}
    /** Speak one of {@code moves} in order of preference; {@code turn} is set for topic turns. */
    public record Speak(Side speaker, List<ResourceLocation> moves, @Nullable ConversationTopic.Turn turn,
                        @Nullable Binding binding, boolean finalLine, boolean bridge) implements Step {}
    public record Pause(int ticks) implements Step {}
    public record End(String reason) implements Step {}
    /** A topic that reached a terminal turn; the runtime applies its outcome. */
    public record Completion(ConversationTopic topic, ConversationTopic.Turn turn, @Nullable Binding binding, Side initiator) {}

    private enum Phase { OPENING, TOPIC_START, TURN, EXPANSION, CLOSING, DONE }

    private final ConversationEncounter encounter;
    private final Driver driver;
    private final ConversationState state = new ConversationState();
    private final EnumMap<Side, LineComposer.Balance> balances = new EnumMap<>(Side.class);
    private final Set<ResourceLocation> usedTopics = new LinkedHashSet<>();
    private final List<Completion> completions = new ArrayList<>();
    private Phase phase = Phase.OPENING;
    private List<ResourceLocation> sequence;
    private int sequenceIndex;
    private Side sequenceStart = Side.A, last;
    private int lines, topicsDone, topicAttempts, expansions;
    private boolean paused;
    private Binding binding;
    private Side topicInitiator;
    private ConversationTopic.Turn turn;
    private Deque<ConversationTopic.Turn> candidates = new ArrayDeque<>();
    private final Set<ResourceLocation> expansionsDone = new HashSet<>();
    private ResourceLocation followUp;
    private Step current;
    private boolean firstTurnOfTopic, answering;
    /** Each side's interest in going on, 0 to 1. Good exchanges raise it; time, disagreement and flat jokes lower it. */
    private final EnumMap<Side, Double> interest = new EnumMap<>(Side.class);
    private String lastStance, lastReaction;
    private @Nullable String startMood;
    private int clashes;

    public EncounterRun(ConversationEncounter encounter, Driver driver, @Nullable ConversationTopic forced) {
        this.encounter = encounter;
        this.driver = driver;
        Random random = driver.random();
        balances.put(Side.A, new LineComposer.Balance(random.nextDouble()));
        balances.put(Side.B, new LineComposer.Balance(random.nextDouble()));
        List<ConversationEncounter.Sequence> openings = driver.greetedToday() && !encounter.openingAgain().isEmpty()
                ? encounter.openingAgain() : encounter.opening();
        sequence = LineComposer.weighted(openings, ConversationEncounter.Sequence::weight, random).moves();
        this.forced = forced;
        // An idle chat can be anything from a quick hello to a long talk; a hangout starts settled.
        double spread = encounter.context() == ConversationEncounter.Context.HANGOUT ? 0.1 : 0.25;
        for (Side side : Side.values())
            interest.put(side, clamp(driver.interest(side) + START_BONUS + random.nextGaussian() * spread));
        startMood = driver.mood();
        updateMood();
    }
    private final @Nullable ConversationTopic forced;

    public ConversationState state() { return state; }
    public LineComposer.Balance balance(Side side) { return balances.get(side); }
    public int lines() { return lines; }
    public @Nullable Binding binding() { return binding; }
    public @Nullable Side topicInitiator() { return topicInitiator; }
    public boolean done() { return phase == Phase.DONE; }
    public double interest(Side side) { return interest.get(side); }
    /** The interest of the less interested side, which decides when talk winds down. */
    public double interest() { return Math.min(interest.get(Side.A), interest.get(Side.B)); }

    static final double START_BONUS = 0.25, IDLE_DECAY = 0.035, HANGOUT_DECAY = 0.012;

    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }

    private void feel(Side side, double delta) { interest.put(side, clamp(interest.get(side) + delta)); }

    /** How the last line changed each side's interest. */
    private void react(Speak speak, @Nullable Side previous) {
        boolean hangout = encounter.context() == ConversationEncounter.Context.HANGOUT;
        double decay = hangout ? HANGOUT_DECAY : IDLE_DECAY;
        for (Side side : Side.values()) feel(side, -decay);
        String stance = state.get(ConversationState.STANCE);
        if (stance != null && lastStance != null && !"0".equals(stance) && !"0".equals(lastStance)) {
            double delta = stance.equals(lastStance) ? 0.04 : -0.05;
            if (!stance.equals(lastStance) && speak.speaker() != previous) clashes++;
            feel(Side.A, delta);
            feel(Side.B, delta);
        }
        lastStance = stance;
        String reaction = state.get("reaction");
        if (reaction != null && !reaction.equals(lastReaction)) {
            Side teller = speak.speaker().other();
            if (reaction.equals("praise")) { feel(teller, 0.12); feel(speak.speaker(), 0.05); }
            if (reaction.equals("groan")) { feel(teller, -0.08); feel(speak.speaker(), -0.03); }
        }
        lastReaction = reaction;
        if (state.get(ConversationState.ANSWERED) != null) feel(speak.speaker().other(), 0.03);
        updateMood();
    }

    /** Tense after repeated disagreement or from a strained start, warm when both are engaged, flat when neither is. */
    private void updateMood() {
        double low = interest(), high = Math.max(interest.get(Side.A), interest.get(Side.B));
        String mood;
        if (clashes >= 2 || "tense".equals(startMood) && lines < 4) mood = "tense";
        else if (low >= 0.6 || "warm".equals(startMood) && low >= 0.35) mood = "warm";
        else if (high < 0.3) mood = "flat";
        else mood = null;
        state.put(ConversationState.MOOD, mood);
    }

    /** Topics that finished since the last call. */
    public List<Completion> drainCompletions() {
        List<Completion> out = List.copyOf(completions);
        completions.clear();
        return out;
    }

    public Step next() {
        if (current != null) return current;
        current = plan();
        return current;
    }

    /** The step was spoken with {@code move}. */
    public void spoke(ResourceLocation move) {
        Step step = current;
        current = null;
        if (!(step instanceof Speak speak)) return;
        Side previous = last;
        lines++;
        last = speak.speaker();
        react(speak, previous);
        // An authored set piece is not built from parts, so it opens nothing and answers nothing.
        if (speak.turn() != null && !speak.turn().lines().isEmpty() && move.equals(speak.turn().move())) state.clearExpectation();
        if (answering) { answering = false; return; }
        switch (phase) {
            case OPENING, CLOSING -> sequenceIndex++;
            case TURN -> afterTurn(speak);
            case EXPANSION -> {
                ConversationMove def = driver.move(move);
                if (followUp == null && def != null && def.followUp() != null) followUp = def.followUp();
                else followUp = null;
            }
            default -> {}
        }
    }

    /** The step could not be spoken by any of its moves. */
    public void failed() {
        Step step = current;
        current = null;
        if (!(step instanceof Speak speak)) return;
        if (answering) {
            answering = false;
            state.put(ConversationState.PENDING, null);
            state.clearExpectation();
            return;
        }
        switch (phase) {
            case OPENING, CLOSING -> sequenceIndex++;
            case TURN -> {
                if (!candidates.isEmpty()) turn = candidates.poll();
                else if (firstTurnOfTopic) { endTopicEarly(); }
                else finishTopic(null);
            }
            case EXPANSION -> followUp = null;
            default -> {}
        }
    }

    public void cancel() {
        phase = Phase.DONE;
        current = null;
    }

    private Step plan() {
        int cap = encounter.context() == ConversationEncounter.Context.HANGOUT ? HANGOUT_LINE_CAP : IDLE_LINE_CAP;
        if (phase != Phase.CLOSING && phase != Phase.DONE && lines >= cap) startClosing();
        // An open question, or a pair that must be answered, is answered before the subject changes:
        // its replies belong to this subject.
        boolean staysOnSubject = phase == Phase.OPENING || phase == Phase.TURN || phase == Phase.EXPANSION && followUp != null;
        boolean owed = state.pending() != null || state.replyOwed();
        if (owed && last != null && !staysOnSubject) return owedReply();
        Step step = planStep();
        // The reply goes at the start of the other speaker's next line. Only when the same speaker
        // would talk again, or nobody would, does the other speaker reply on their own.
        if (owed && last != null && (!(step instanceof Speak speak) || speak.speaker() == last)) return owedReply();
        return step;
    }

    private Speak owedReply() {
        answering = true;
        return new Speak(last.other(), List.of(state.pending() != null ? ANSWER : ACKNOWLEDGE), null, binding, false, false);
    }

    /** Planning again is safe: a topic bound here stays bound and its turn is planned next time. */
    private Step planStep() {
        while (true) {
            switch (phase) {
                case OPENING -> {
                    if (sequenceIndex < sequence.size()) {
                        Side side = (sequenceIndex % 2 == 0) ? sequenceStart : sequenceStart.other();
                        return new Speak(side, List.of(sequence.get(sequenceIndex)), null, null, false, false);
                    }
                    phase = Phase.TOPIC_START;
                }
                case TOPIC_START -> {
                    Step step = startTopic();
                    if (step != null) return step;
                }
                case TURN -> {
                    Side side = sideOf(turn.speaker());
                    boolean bridge = firstTurnOfTopic && topicsDone > 0 && encounter.bridgePool() != null;
                    return new Speak(side, List.of(turn.move()), turn, binding, false, bridge);
                }
                case EXPANSION -> {
                    Step step = expansion();
                    if (step != null) return step;
                }
                case CLOSING -> {
                    if (sequenceIndex < sequence.size()) {
                        Side side = (sequenceIndex % 2 == 0) ? sequenceStart : sequenceStart.other();
                        ResourceLocation move = sequence.get(sequenceIndex);
                        ConversationMove def = driver.move(move);
                        return new Speak(side, List.of(move), null, null, def == null || def.finalLine(), false);
                    }
                    phase = Phase.DONE;
                    return new End("closed");
                }
                case DONE -> { return new End("done"); }
            }
        }
    }

    private @Nullable Step startTopic() {
        boolean hangout = encounter.context() == ConversationEncounter.Context.HANGOUT;
        if (hangout && driver.leaving() != null) { startClosing(); return null; }
        if (topicsDone >= encounter.topicsMax() || topicAttempts >= TOPIC_ATTEMPTS) { startClosing(); return null; }
        // An idle chat ends when the less interested side has had enough. With almost no interest,
        // a greeting can be the whole conversation.
        if (!hangout && topicsDone == 0 && lines > 0 && interest() < QUICK_HELLO) { startClosing(); return null; }
        if (!hangout && topicsDone >= Math.max(1, encounter.topicsMin()) && driver.random().nextDouble() > interest()) {
            startClosing();
            return null;
        }
        // At a hangout, low interest brings a quiet spell instead of a goodbye, and a rest restores some interest.
        double pauseChance = encounter.pause().chance() + (hangout && interest() < 0.3 ? 0.4 : 0);
        if (hangout && topicsDone > 0 && !paused && state.pending() == null && driver.random().nextDouble() < pauseChance) {
            paused = true;
            for (Side side : Side.values()) feel(side, 0.12);
            return new Pause(100 + driver.random().nextInt(200));
        }
        paused = false;
        Side speaker = last == null ? Side.A : last.other();
        Binding next = null;
        if (forced != null && topicsDone == 0 && topicAttempts == 0) next = new Binding(forced, null, null);
        if (next == null) next = driver.bindTopic(speaker, usedTopics, binding == null ? null : binding.subjectKind());
        topicAttempts++;
        if (next == null) {
            if (topicsDone == 0) { phase = Phase.DONE; return new End("no_topic"); }
            startClosing();
            return null;
        }
        ResourceLocation previous = binding == null ? null : binding.subjectKind();
        String previousRegister = binding == null ? null : binding.topic().register();
        state.newSubject();
        // What the talk just left, so bridges can fit it: "Anyway, enough gloom." after grave news.
        state.put(ConversationState.AFTER, previousRegister == null || previousRegister.isEmpty() ? null : previousRegister);
        if (previous != null && next.subjectKind() != null && associated(next.topic(), previous)) state.put(ConversationState.ASSOC, "true");
        binding = next;
        topicInitiator = speaker;
        usedTopics.add(next.topic().id());
        turn = next.topic().turns().get(next.topic().start());
        candidates = new ArrayDeque<>();
        firstTurnOfTopic = true;
        phase = Phase.TURN;
        return null;
    }

    private boolean associated(ConversationTopic topic, ResourceLocation previousKind) {
        return topic.associated().stream().anyMatch(a -> a.equals(previousKind.toString()));
    }

    private void afterTurn(Speak speak) {
        ConversationTopic.Turn spoken = turn;
        firstTurnOfTopic = false;
        if (spoken.terminal()) { finishTopic(spoken); return; }
        List<ConversationTopic.Turn> replies = new ArrayList<>();
        for (String id : spoken.replies()) replies.add(binding.topic().turns().get(id));
        candidates = new ArrayDeque<>(ordered(replies));
        turn = candidates.poll();
        if (turn == null) finishTopic(null);
    }

    private List<ConversationTopic.Turn> ordered(List<ConversationTopic.Turn> replies) {
        List<ConversationTopic.Turn> pool = new ArrayList<>();
        Map<ConversationTopic.Turn, Double> weights = new HashMap<>();
        for (ConversationTopic.Turn reply : replies) {
            double w = reply.weight() * driver.replyWeight(reply, sideOf(reply.speaker()));
            if (w > 0) { pool.add(reply); weights.put(reply, w); }
        }
        List<ConversationTopic.Turn> order = new ArrayList<>();
        while (!pool.isEmpty()) {
            ConversationTopic.Turn pick = LineComposer.weighted(pool, weights::get, driver.random());
            order.add(pick);
            pool.remove(pick);
        }
        return order;
    }

    private void finishTopic(@Nullable ConversationTopic.Turn terminal) {
        if (terminal != null) completions.add(new Completion(binding.topic(), terminal, binding, topicInitiator));
        topicsDone++;
        topicAttempts = 0;
        expansions = 0;
        expansionsDone.clear();
        followUp = null;
        phase = Phase.EXPANSION;
    }

    private void endTopicEarly() {
        usedTopics.add(binding.topic().id());
        phase = Phase.TOPIC_START;
    }

    private @Nullable Step expansion() {
        Side next = last == null ? Side.A : last.other();
        if (followUp != null) {
            ResourceLocation move = followUp;
            return new Speak(next, List.of(move), null, binding, false, false);
        }
        ConversationTopic topic = binding.topic();
        int max = Math.min(topic.expansionMax(), encounter.expansionsMax());
        if (expansions >= max || topic.expansionMoves().isEmpty() || driver.random().nextDouble() > interest()) {
            phase = Phase.TOPIC_START;
            return null;
        }
        List<ResourceLocation> options = new ArrayList<>();
        for (ResourceLocation move : topic.expansionMoves()) {
            if (expansionsDone.contains(move)) continue;
            ConversationMove def = driver.move(move);
            if (def == null || def.initiatorOnly() && next != topicInitiator) continue;
            options.add(move);
        }
        if (options.isEmpty()) { phase = Phase.TOPIC_START; return null; }
        Collections.shuffle(options, driver.random());
        expansions++;
        expansionsDone.add(options.get(0));
        return new Speak(next, options, null, binding, false, false);
    }

    private void startClosing() {
        double now = interest();
        List<ConversationEncounter.Sequence> fits = encounter.closing().stream().filter(s -> s.fits(lines, now)).toList();
        if (fits.isEmpty()) fits = encounter.closing().stream().filter(s -> s.fits(lines)).toList();
        if (fits.isEmpty()) fits = encounter.closing();
        // After a lone greeting, walking off in silence is rude: the other side at least says goodbye.
        if (lines == 1 && fits.stream().anyMatch(s -> !s.moves().isEmpty())) fits = fits.stream().filter(s -> !s.moves().isEmpty()).toList();
        else if (lines == 1) fits = encounter.closing().stream().filter(s -> !s.moves().isEmpty()).toList();
        if (fits.isEmpty()) fits = encounter.closing();
        sequence = LineComposer.weighted(fits, ConversationEncounter.Sequence::weight, driver.random()).moves();
        sequenceIndex = 0;
        Side leaving = driver.leaving();
        Side preferred = last == null ? Side.A : last.other();
        sequenceStart = leaving != null && (leaving == preferred || driver.random().nextDouble() < 0.15) ? leaving : preferred;
        phase = Phase.CLOSING;
    }

    private Side sideOf(ConversationTopic.Speaker speaker) {
        return speaker == ConversationTopic.Speaker.INITIATOR ? topicInitiator : topicInitiator.other();
    }
}
