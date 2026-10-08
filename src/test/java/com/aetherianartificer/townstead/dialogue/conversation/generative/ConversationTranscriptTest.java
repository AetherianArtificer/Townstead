package com.aetherianartificer.townstead.dialogue.conversation.generative;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Writes readable sample conversations from the shipped data to build/conversation-transcript.txt. One simulated
 * village lives through several days, so news spreads, needs change and pairs remember what they discussed.
 * Set -Dtownstead.transcript.seed, -Dtownstead.transcript.count and -Dtownstead.transcript.perDay to vary the sample.
 * With a culture pack on TOWNSTEAD_DIALOGUE_PACKS, the village takes that culture; -Dtownstead.transcript.culture= overrides it.
 */
class ConversationTranscriptTest {
    @Test void writeTranscript() throws Exception {
        long seed = Long.getLong("townstead.transcript.seed", System.currentTimeMillis());
        int count = Integer.getInteger("townstead.transcript.count", 16);
        int perDay = Math.max(1, Integer.getInteger("townstead.transcript.perDay", 4));
        EncounterSimulationTest.load();
        GenerativeDialogue.Data data = ShippedDialogue.data();
        Random random = new Random(seed);
        SimVillage village = new SimVillage(random);
        // A culture pack on TOWNSTEAD_DIALOGUE_PACKS gives its culture to the village, so its voice shows.
        village.culture = System.getProperty("townstead.transcript.culture", data.voices().values().stream()
                .filter(v -> !v.id().equals(GenerativeDialogue.COMMON_VOICE) && !v.cultures().isEmpty())
                .map(v -> v.cultures().iterator().next()).findFirst().orElse(""));
        // -Dtownstead.transcript.infect=true starts one villager freshly bitten, to follow an infection through.
        if (Boolean.getBoolean("townstead.transcript.infect")) village.bite(village.roster.get(0));
        StringBuilder out = new StringBuilder("seed " + seed + "\n").append(village.describe()).append('\n');
        for (int i = 0; i < count; i++) {
            if (i > 0 && i % perDay == 0) village.nextDay();
            if (i % perDay == 0) out.append("--- ").append(village.today()).append(" ---\n\n");
            ConversationEncounter encounter = data.encounter(random.nextInt(3) == 0 ? ConversationEncounter.Context.HANGOUT
                    : ConversationEncounter.Context.IDLE);
            SimVillage.Villager[] pair = village.pair();
            List<String> log = new ArrayList<>();
            List<EncounterSimulationTest.Spoken> spoken = EncounterSimulationTest.encounter(encounter, village, pair[0], pair[1], log);
            out.append("=== ").append(log.get(0)).append('\n');
            for (EncounterSimulationTest.Spoken line : spoken) {
                out.append(String.format(Locale.ROOT, "  %-8s %s%n", line.speakerName() + ":", render(line)));
            }
            out.append('\n');
        }
        Path file = Path.of("build", "conversation-transcript.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, out);
        System.out.println(out);
    }

    private static String render(EncounterSimulationTest.Spoken line) {
        Map<String, DialogueText.Value> values = new HashMap<>();
        values.put("self", new DialogueText.Value(line.speakerName(), null, Map.of()));
        values.put("other", new DialogueText.Value(line.listenerName(), null, Map.of()));
        if (line.subject() != null) line.subject().slots().forEach((name, slot) ->
                values.put(name, new DialogueText.Value(slot.english(), null, slot.meta())));
        StringBuilder text = new StringBuilder();
        for (DialoguePart part : line.line().parts()) {
            if (text.length() > 0) text.append(' ');
            text.append(DialogueText.capitalize(DialogueText.fill(part.english(), values, "en_us")));
        }
        return text.toString();
    }
}
