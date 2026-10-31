package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.aetherianartificer.townstead.dialogue.contextual.DialogueRequest;
import com.aetherianartificer.townstead.dialogue.conversation.ConversationTopic;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs whole encounters through the real planner and composer with the shipped data, and checks the
 * coherence rules the prototype measured. A failure prints the conversation that broke the rule.
 */
class EncounterSimulationTest {
    private static GenerativeDialogue.Data data;
    private static Map<ResourceLocation, ConversationTopic> topics;
    @BeforeAll static void load() throws Exception {
        data = ShippedDialogue.data();
        topics = ShippedDialogue.topics();
    }

    record Spoken(EncounterRun.Side side, ResourceLocation move, LineComposer.Line line, @Nullable LineComposer.Subject subject,
                  String speakerName, String listenerName) {}

    static final double GOAL_WEIGHT = 3;

    /** One conversation between two villagers of the simulated village, driven the way the engine drives it. */
    static List<Spoken> encounter(ConversationEncounter encounter, SimVillage village, SimVillage.Villager va,
                                  SimVillage.Villager vb, List<String> log) {
        Random random = village.random;
        UUID a = va.id(), b = vb.id();
        boolean hangout = encounter.context() == ConversationEncounter.Context.HANGOUT;
        int[] clock = {0};
        List<ResourceLocation> topicsUsed = new ArrayList<>();
        List<LineComposer.Subject> told = new ArrayList<>();
        EncounterRun.Driver driver = new EncounterRun.Driver() {
            @Override public @Nullable EncounterRun.Binding bindTopic(EncounterRun.Side side, Set<ResourceLocation> used, @Nullable ResourceLocation previous) {
                SimVillage.Villager speaker = side == EncounterRun.Side.A ? va : vb, listener = speaker == va ? vb : va;
                List<EncounterRun.Binding> options = new ArrayList<>();
                Map<EncounterRun.Binding, Double> weights = new HashMap<>();
                for (ConversationTopic topic : topics.values()) {
                    if (used.contains(topic.id())) continue;
                    if (topic.id().getPath().equals("venue") && !hangout) continue;
                    double weight = topic.weight() * replyWeight(topic.turns().get(topic.start()), side);
                    if (weight <= 0) continue;
                    if ("humor".equals(topic.register())) {
                        boolean grave = used.stream().map(topics::get).anyMatch(t -> t != null && "grief".equals(t.register()));
                        weight *= grave ? 0.1 : village.infection(speaker) >= 0.2f ? 0.2 : 1;
                    }
                    // Stable personal tastes and a dislike of repeating what this pair already discussed, as in the engine.
                    weight *= (0.65 + Math.floorMod(Objects.hash(speaker.id(), topic.id()), 101) / 100D)
                            / (1 + village.repeats(speaker, listener, topic.id()) * 3);
                    if (topic.subjects().isEmpty()) {
                        EncounterRun.Binding binding = new EncounterRun.Binding(topic, null, topic.id());
                        options.add(binding); weights.put(binding, weight);
                        continue;
                    }
                    ResourceLocation kind = ResourceLocation.tryParse(topic.subjects().get(0));
                    List<LineComposer.Subject> found = village.subjects(kind, speaker, listener);
                    if (found.isEmpty()) continue;
                    LineComposer.Subject subject = pick(found, x -> Math.max(0.1, 1 + GOAL_WEIGHT * x.urgency()), random);
                    EncounterRun.Binding binding = new EncounterRun.Binding(topic, subject, kind);
                    options.add(binding); weights.put(binding, weight * Math.max(0.1, 1 + GOAL_WEIGHT * subject.urgency()));
                }
                if (options.isEmpty()) return null;
                EncounterRun.Binding chosen = pick(options, weights::get, random);
                topicsUsed.add(chosen.topic().id());
                if (chosen.subject() != null) told.add(chosen.subject());
                return chosen;
            }
            @Override public double replyWeight(ConversationTopic.Turn turn, EncounterRun.Side speaker) {
                String p = (speaker == EncounterRun.Side.A ? va : vb).personality();
                return turn.gate().weight(new DialogueRequest("x", Set.of(), Set.of(p), Set.of()), c -> true);
            }
            @Override public double chattiness() {
                return encounter.chattiness().of(village.meetings(va, vb), List.of(va.personality(), vb.personality()));
            }
            @Override public double interest(EncounterRun.Side side) {
                SimVillage.Villager self = side == EncounterRun.Side.A ? va : vb, other = self == va ? vb : va;
                double value = encounter.chattiness().of(village.meetings(self, other), List.of(self.personality()));
                value += Math.max(-0.2, Math.min(0.2, village.affection(self, other) / 60));
                if (village.need(self) != null) value -= 0.1;
                return value + com.aetherianartificer.townstead.dialogue.conversation.ConversationEngine.infectionInterest(
                        self.personality(), village.infection(self), village.infection(other), village.affection(self, other));
            }
            @Override public @Nullable String mood() {
                double affection = village.affection(va, vb);
                boolean fever = village.infection(va) >= 0.2f || village.infection(vb) >= 0.2f;
                if (fever) return affection >= 25 ? null : "tense";
                return affection >= 25 ? "warm" : affection <= -10 ? "tense" : null;
            }
            @Override public boolean greetedToday() { return village.greetedToday(va, vb); }
            @Override public @Nullable EncounterRun.Side leaving() { return hangout && clock[0] > 12 + random.nextInt(10) ? EncounterRun.Side.B : null; }
            @Override public @Nullable ConversationMove move(ResourceLocation id) { return data.moves().get(id); }
            @Override public Random random() { return random; }
        };
        EncounterRun run = new EncounterRun(encounter, driver, null);
        village.setting(hangout);
        if (village.venue != null) run.state().put(ConversationState.VENUE, village.venue.kind());
        LineComposer.World world = new LineComposer.World() {
            @Override public boolean requirement(String requirement) { return village.requirement(requirement); }
            @Override public double gate(ConversationTopic.Gate gate) {
                return gate.weight(new DialogueRequest("x", Set.of(), Set.of(), Set.of()), c -> true);
            }
            @Override public boolean filtered(Set<String> tags) { return false; }
        };
        String mood = driver.mood();
        int met = village.meetings(va, vb);
        log.add(va.name() + " (" + va.personality() + ", " + va.stage() + ") and " + vb.name() + " (" + vb.personality() + ", "
                + vb.stage() + "), " + (village.venue != null ? "hangout at the " + village.venue.name() : "idle") + (village.night ? ", night" : ", day") + (mood == null ? "" : ", " + mood)
                + (met == 0 ? ", first talk" : ", talked " + met + "x before"));
        List<Spoken> spoken = new ArrayList<>();
        for (int guard = 0; guard < 400; guard++) {
            EncounterRun.Step step = run.next();
            if (step instanceof EncounterRun.End) break;
            if (step instanceof EncounterRun.Pause) { run.spoke(ConversationTopic.SAY); continue; }
            EncounterRun.Speak speak = (EncounterRun.Speak) step;
            SimVillage.Villager speakerV = speak.speaker() == EncounterRun.Side.A ? va : vb;
            UUID speaker = speakerV.id(), listener = speaker.equals(a) ? b : a;
            EncounterRun.Binding binding = speak.binding();
            // The register and voice, chosen as the runtime chooses them.
            String register = speak.turn() != null && !speak.turn().register().isEmpty() ? speak.turn().register()
                    : binding != null && binding.subjectKind() != null && data.subjects().containsKey(binding.subjectKind())
                    ? data.subjects().get(binding.subjectKind()).register()
                    : binding != null ? binding.topic().register() : "ambient";
            List<ResourceLocation> voices = data.voiceChain(village.culture);
            DialogueVoice own = data.voices().get(voices.get(0));
            LineComposer.Speaker facts = new LineComposer.Speaker(speaker, speakerV.stage(), speakerV.stage(), speakerV.personality(),
                    voices, own == null ? DialogueVoice.Children.SIMPLE : own.children(),
                    own == null ? 0 : own.target(register, speakerV.stage(), speakerV.stage(), Set.of()));
            EncounterRun.Side initiatorSide = run.topicInitiator();
            UUID initiator = initiatorSide == null ? speaker : (initiatorSide == EncounterRun.Side.A ? a : b);
            LineComposer.Subject subject = binding == null ? null : binding.subject();
            LineComposer.Line line = null;
            ResourceLocation used = null;
            for (ResourceLocation move : speak.moves()) {
                LineComposer.Request req = new LineComposer.Request(move, facts, listener, initiator, subject, register,
                        speak.finalLine(), speak.bridge() ? encounter.bridgePool() : null, run.state(), run.balance(speak.speaker()), random);
                line = LineComposer.compose(data, req, world, village);
                if (line != null) { used = move; break; }
            }
            if (line == null) { run.failed(); continue; }
            run.spoke(used);
            clock[0]++;
            spoken.add(new Spoken(speak.speaker(), used, line, subject, speakerV.name(), speakerV == va ? vb.name() : va.name()));
            log.add(speak.speaker() + " " + used + ": " + line.parts().stream().map(DialoguePart::key).toList());
        }
        village.talked(va, vb, told, topicsUsed);
        return spoken;
    }

    private static <T> T pick(List<T> options, java.util.function.ToDoubleFunction<T> weights, Random random) {
        double total = 0;
        for (T option : options) total += Math.max(0, weights.applyAsDouble(option));
        double roll = random.nextDouble() * total;
        for (T option : options) if ((roll -= Math.max(0, weights.applyAsDouble(option))) <= 0) return option;
        return options.get(options.size() - 1);
    }

    @Test void encountersFollowTheCoherenceRules() {
        Random random = new Random(7);
        int lines = 0, encounters = 0;
        for (ConversationEncounter.Context context : ConversationEncounter.Context.values()) {
            ConversationEncounter encounter = data.encounter(context);
            SimVillage village = null;
            for (int i = 0; i < 300; i++) {
                if (i % 60 == 0) village = new SimVillage(random);
                else if (i % 6 == 0) village.nextDay();
                SimVillage.Villager[] duo = village.pair();
                List<String> log = new ArrayList<>();
                List<Spoken> spoken = encounter(encounter, village, duo[0], duo[1], log);
                encounters++;
                lines += spoken.size();
                String where = String.join("\n", log);
                for (int n = 0; n < spoken.size(); n++) {
                    Spoken line = spoken.get(n);
                    List<DialoguePart> parts = line.line().parts();
                    assertFalse(parts.isEmpty(), "empty line\n" + where);
                    for (int p = 0; p < parts.size() - 1; p++) assertNull(parts.get(p).asks(), "question before the end of a line\n" + where);
                    assertTrue(parts.stream().filter(pt -> pt.act() == DialoguePart.Act.GREET).count() <= 1, "two greetings\n" + where);
                    String asks = parts.get(parts.size() - 1).asks();
                    if (asks != null) {
                        assertTrue(n + 1 < spoken.size(), "conversation ended on a question\n" + where);
                        Spoken next = spoken.get(n + 1);
                        assertNotEquals(line.side(), next.side(), "a speaker answered their own question\n" + where);
                        assertTrue(next.line().parts().stream().anyMatch(pt -> asks.equals(pt.answers())),
                                "question " + asks + " left unanswered\n" + where);
                    }
                    ResourceLocation opened = opened(parts);
                    ConversationPair pair = opened == null || asks != null ? null : data.pairs().get(opened);
                    if (pair != null && pair.required()) {
                        assertTrue(n + 1 < spoken.size(), "conversation ended on an open " + opened + "\n" + where);
                        Spoken next = spoken.get(n + 1);
                        assertNotEquals(line.side(), next.side(), "a speaker replied to their own " + opened + "\n" + where);
                        assertTrue(next.line().parts().stream().anyMatch(pt -> pt.responds().contains(opened)),
                                opened + " left without a reply\n" + where);
                    }
                    if (n >= 2 && hasRole(line.move(), ConversationMove.Role.OPENING)) fail("greeting after the opening\n" + where);
                    if (n < spoken.size() - 3 && hasRole(line.move(), ConversationMove.Role.CLOSING)
                            && !line.move().getPath().equals("close_discreet")) fail("goodbye before the end\n" + where);
                }
            }
        }
        assertTrue(lines > encounters * 3, "encounters should be conversations, got " + lines + " lines in " + encounters);
    }

    @Test void infectedVillagersTalkAboutIt() {
        Random random = new Random(11);
        SimVillage village = new SimVillage(random);
        SimVillage.Villager sick = village.roster.get(0), friend = village.roster.get(1);
        village.infection.put(sick.id(), 0.3f);
        Map<String, Integer> pools = new TreeMap<>();
        ConversationEncounter idle = data.encounter(ConversationEncounter.Context.IDLE);
        for (int i = 0; i < 80; i++) {
            List<Spoken> spoken = encounter(idle, village, i % 2 == 0 ? sick : friend, i % 2 == 0 ? friend : sick, new ArrayList<>());
            for (Spoken line : spoken) for (DialoguePart part : line.line().parts())
                if (part.pool().getPath().startsWith("infection")) pools.merge(part.pool().getPath(), 1, Integer::sum);
        }
        assertTrue(pools.getOrDefault("infection_claim", 0) > 0, "no infection talk: " + pools);
        assertTrue(pools.getOrDefault("infection_console", 0) + pools.getOrDefault("infection_respond", 0) > 0, "no replies: " + pools);
    }

    static @Nullable ResourceLocation opened(List<DialoguePart> parts) {
        ResourceLocation opened = null;
        for (DialoguePart part : parts) if (part.opens() != null) opened = part.opens();
        return opened;
    }

    private static boolean hasRole(ResourceLocation move, ConversationMove.Role role) {
        ConversationMove def = data.moves().get(move);
        return def != null && def.role() == role;
    }
}
