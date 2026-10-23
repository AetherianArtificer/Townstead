package com.aetherianartificer.townstead.dialogue.conversation;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.dialogue.contextual.DialogueSelector;
import com.aetherianartificer.townstead.dialogue.conversation.generative.*;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Runs whole encounters at once through the real engine with real villagers, as a dry run. Nothing reaches
 * the world: no chat, gestures, rewards, memories, story spread or cooldowns. The results go to a transcript.
 */
final class ConversationSimulation {
    private ConversationSimulation() {}

    static final Path TRANSCRIPT = Path.of("logs", "townstead-conversations.txt");

    record Line(String speaker, ResourceLocation move, String english, LineComposer.Line composed, boolean speakerIsA) {}
    record Encounter(String a, String b, boolean hangout, String reason, List<Line> lines, List<String> topics, int failed) {}

    /** Counts across a run, for the chat summary. */
    static final class Report {
        final List<Encounter> encounters = new ArrayList<>();
        final Map<ResourceLocation, Integer> pools = new HashMap<>();
        final Set<ResourceLocation> topics = new HashSet<>();
        int lines, failed, unanswered, unreplied, setPieces;

        void add(Encounter encounter) {
            encounters.add(encounter);
            lines += encounter.lines().size();
            failed += encounter.failed();
            encounter.topics().forEach(t -> topics.add(ResourceLocation.tryParse(t)));
            List<Line> spoken = encounter.lines();
            for (int n = 0; n < spoken.size(); n++) {
                LineComposer.Line line = spoken.get(n).composed();
                if (line == null) { setPieces++; continue; }
                for (DialoguePart part : line.parts()) pools.merge(part.pool(), 1, Integer::sum);
                String asks = line.parts().isEmpty() ? null : line.parts().get(line.parts().size() - 1).asks();
                if (asks != null && !answered(spoken, n, asks)) unanswered++;
                ResourceLocation opened = null;
                for (DialoguePart part : line.parts()) if (part.opens() != null) opened = part.opens();
                ConversationPair pair = opened == null || asks != null ? null : GenerativeDialogue.data().pairs().get(opened);
                if (pair != null && pair.required() && !replied(spoken, n, opened)) unreplied++;
            }
        }

        private static boolean replied(List<Line> spoken, int n, ResourceLocation pair) {
            if (n + 1 >= spoken.size()) return false;
            Line next = spoken.get(n + 1);
            if (next.speakerIsA() == spoken.get(n).speakerIsA()) return false;
            return next.composed() == null || next.composed().parts().stream().anyMatch(p -> p.responds().contains(pair));
        }

        private static boolean answered(List<Line> spoken, int n, String asks) {
            if (n + 1 >= spoken.size()) return false;
            Line next = spoken.get(n + 1);
            if (next.speakerIsA() == spoken.get(n).speakerIsA()) return false;
            return next.composed() == null || next.composed().parts().stream().anyMatch(p -> asks.equals(p.answers()));
        }
    }

    /** One encounter between two villagers, stepped to its end without waiting. */
    static Encounter run(ServerLevel level, ConversationEngine.Runtime runtime, DialogueSelector selector,
                         VillagerEntityMCA a, VillagerEntityMCA b, Random random) {
        GenerativeDialogue.Data data = GenerativeDialogue.data();
        boolean hangout = ConversationEngine.sharedVisit(a, b);
        ConversationEncounter encounter = data.encounter(hangout ? ConversationEncounter.Context.HANGOUT
                : ConversationEncounter.Context.IDLE);
        if (encounter == null) encounter = data.encounter(ConversationEncounter.Context.IDLE);
        long start = level.getGameTime();
        ConversationEngine.Live live = new ConversationEngine.Live(a.getUUID(), b.getUUID(), true, hangout, random, start);
        live.simulated = true;
        live.clock = start;
        live.run = new EncounterRun(encounter, new ConversationEngine.Driver(level, runtime, live, encounter), null);
        if (hangout) live.run.state().put(ConversationState.VENUE, ConversationEngine.venueKind(a));

        List<Line> lines = new ArrayList<>();
        List<String> topics = new ArrayList<>();
        int failed = 0;
        String reason = "step_limit";
        for (int guard = 0; guard < 400; guard++) {
            if (live.clock >= live.deadline) { reason = "timeout"; break; }
            EncounterRun.Step step = live.run.next();
            if (step instanceof EncounterRun.End end) { reason = end.reason(); break; }
            if (step instanceof EncounterRun.Pause pause) {
                live.run.spoke(ConversationTopic.SAY);
                live.clock += pause.ticks();
                continue;
            }
            EncounterRun.Speak speak = (EncounterRun.Speak) step;
            VillagerEntityMCA speaker = speak.speaker() == EncounterRun.Side.A ? a : b;
            VillagerEntityMCA listener = speaker == a ? b : a;
            ConversationEngine.Said said = ConversationEngine.utter(level, runtime, live, speak, speaker, listener, selector, live.clock);
            if (said == null) { live.run.failed(); failed++; continue; }
            if (speak.binding() != null) {
                String topic = speak.binding().topic().id().toString();
                if (topics.isEmpty() || !topics.get(topics.size() - 1).equals(topic)) topics.add(topic);
            }
            ConversationMove def = data.moves().get(said.move());
            if (def != null && def.role() == ConversationMove.Role.OPENING) {
                runtime.greeted.put(ConversationEngine.pairKey(live), com.aetherianartificer.townstead.calendar.TownsteadCalendar.worldDay(level.getServer()));
            }
            live.run.spoke(said.move());
            live.run.drainCompletions();
            lines.add(new Line(speaker.getName().getString(), said.move(), said.rendered().english(), said.line(), speaker == a));
            live.clock += ConversationRuntime.duration(said.rendered().english(), speak.turn() == null ? -1 : speak.turn().duration());
        }
        live.run.cancel();
        return new Encounter(a.getName().getString(), b.getName().getString(), hangout, reason, lines, topics, failed);
    }

    /** Random pairs, in either order. Distance is not checked: a dry run needs no line of sight. */
    static List<VillagerEntityMCA[]> pairs(List<VillagerEntityMCA> villagers, int count, Random random) {
        List<VillagerEntityMCA[]> out = new ArrayList<>();
        if (villagers.size() < 2) return out;
        for (int i = 0; i < count; i++) {
            int first = random.nextInt(villagers.size()), second = random.nextInt(villagers.size() - 1);
            if (second >= first) second++;
            out.add(new VillagerEntityMCA[]{villagers.get(first), villagers.get(second)});
        }
        return out;
    }

    static Report simulate(ServerLevel level, List<VillagerEntityMCA[]> pairs, long seed) {
        ConversationEngine.Runtime runtime = new ConversationEngine.Runtime();
        DialogueSelector selector = new DialogueSelector();
        Random random = new Random(seed);
        Report report = new Report();
        for (VillagerEntityMCA[] pair : pairs) report.add(run(level, runtime, selector, pair[0], pair[1], new Random(random.nextLong())));
        return report;
    }

    static void write(Report report, long seed) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("Townstead conversation simulation, ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append(", seed ").append(seed).append('\n');
        out.append(summary(report)).append("\n\n");
        int n = 1;
        for (Encounter encounter : report.encounters) {
            out.append("=== ").append(n++).append(". ").append(encounter.a()).append(" and ").append(encounter.b())
                    .append(encounter.hangout() ? " (hangout)" : " (idle)")
                    .append(", ").append(encounter.lines().size()).append(" lines, ended: ").append(encounter.reason());
            if (encounter.failed() > 0) out.append(", failed steps: ").append(encounter.failed());
            out.append('\n');
            if (!encounter.topics().isEmpty()) out.append("    topics: ").append(String.join(", ", encounter.topics())).append('\n');
            for (Line line : encounter.lines()) {
                out.append(String.format(Locale.ROOT, "  %-16s %s%n", line.speaker() + ":", line.english()));
                out.append(String.format(Locale.ROOT, "  %-16s   [%s]%s%n", "", line.move(),
                        line.composed() == null ? " set piece" : " " + line.composed().parts().stream()
                                .map(p -> p.key() == null ? "-" : shortKey(p.key())).toList()));
            }
            out.append('\n');
        }
        Files.createDirectories(TRANSCRIPT.getParent());
        Files.writeString(TRANSCRIPT, out, StandardCharsets.UTF_8);
        Townstead.LOGGER.info("[Conversation] Simulation transcript written to {}", TRANSCRIPT.toAbsolutePath());
    }

    private static String shortKey(String key) {
        int common = key.indexOf(".common.");
        return common >= 0 ? key.substring(common + 8) : key;
    }

    static String summary(Report report) {
        int count = report.encounters.size();
        long hangouts = report.encounters.stream().filter(Encounter::hangout).count();
        double average = count == 0 ? 0 : report.lines / (double) count;
        String busiest = report.pools.entrySet().stream()
                .sorted(Map.Entry.<ResourceLocation, Integer>comparingByValue().reversed()).limit(3)
                .map(e -> e.getKey().getPath() + " " + e.getValue()).reduce((x, y) -> x + ", " + y).orElse("none");
        return String.format(Locale.ROOT, "%d encounters (%d hangout), %d lines, %.1f per encounter, %d topics, "
                        + "%d set-piece lines, %d unanswered questions, %d unreplied pairs, %d failed steps. Busiest pools: %s",
                count, hangouts, report.lines, average, report.topics.size(), report.setPieces, report.unanswered,
                report.unreplied, report.failed, busiest);
    }
}
