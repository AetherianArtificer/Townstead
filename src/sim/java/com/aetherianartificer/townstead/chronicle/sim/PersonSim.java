package com.aetherianartificer.townstead.chronicle.sim;

import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import com.aetherianartificer.townstead.chronicle.model.VillagerMemory;
import com.aetherianartificer.townstead.chronicle.pregen.ChroniclePersonalPregen;
import com.aetherianartificer.townstead.chronicle.pregen.ChroniclePersonalPregen.Beat;
import com.aetherianartificer.townstead.chronicle.scope.ChronicleScopes;
import com.aetherianartificer.townstead.chronicle.scope.ScopeProfile;
import com.aetherianartificer.townstead.chronicle.world.ChronicleSubject;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Fabricates one person's life and prints it, kept beats and forgotten ones alike. */
public final class PersonSim {

    private PersonSim() {}

    public static int run(SimArgs args, SimTemplates.Loaded loaded) {
        int age = args.number("age", 40);
        int daysPerYear = args.number("days-per-year", 360);
        int villageAge = args.number("years", Math.max(age + 20, 120));
        long seed = args.number("seed", 1L);
        ScopeProfile scope = ChronicleScopes.PERSON
                .withThreshold(args.decimal("threshold", ChronicleScopes.PERSON.threshold()));

        long today = (long) villageAge * daysPerYear;
        long birthDay = today - (long) age * daysPerYear;
        RandomSource rng = RandomSource.create(seed);
        SimChronicleWorld world = new SimChronicleWorld(
                loaded.templates(), today, daysPerYear, rng, args.itemTags());

        UUID uuid = uuid(args, rng);
        ChronicleSubject subject = SimRoster.subject(
                args.text("name", SimNames.pick(rng.nextBoolean(), rng)),
                args.text("profession", "townstead:cook"), uuid,
                SimRoster.kind(args.text("kind", "villager")));
        List<ChronicleSubject> others = SimRoster.of(args.number("others", 8), rng);
        VillageKey key = new VillageKey(args.id("dim", "minecraft:overworld"), args.number("village", 1));

        List<Beat> beats = ChroniclePersonalPregen.generate(
                world, subject, birthDay, key, others, scope);

        print(world, subject, beats, birthDay, age, scope, uuid, loaded, others);

        List<String> violations = SimChecks.runLife(beats, age, loaded.templates());
        SimOutput.violations(violations);
        return args.has("check") && !violations.isEmpty() ? 1 : 0;
    }

    private static UUID uuid(SimArgs args, RandomSource rng) {
        String given = args.text("uuid", "");
        if (!given.isEmpty()) {
            try {
                return UUID.fromString(given);
            } catch (IllegalArgumentException ignored) {
                // fall through to a fabricated one
            }
        }
        return new UUID(rng.nextLong(), rng.nextLong());
    }

    private static void print(SimChronicleWorld world, ChronicleSubject subject, List<Beat> beats,
                              long birthDay, int age, ScopeProfile scope, UUID uuid,
                              SimTemplates.Loaded loaded, List<ChronicleSubject> others) {
        SimOutput.heading(String.format(Locale.ROOT, "%s | %s | %s | age %d | born d%d",
                subject.displayName(), subject.kind().name().toLowerCase(Locale.ROOT),
                subject.professionId().isEmpty() ? "no profession" : subject.professionId(),
                age, birthDay));
        System.out.printf(Locale.ROOT,
                "uuid %s%nperson scope: threshold %.1f, capacity %d, decay %.2f/day%n%n",
                uuid, scope.threshold(), scope.capacity(), scope.dailyDecay());

        if (beats.isEmpty()) {
            System.out.println("  (no candidate templates: nothing in the pool fits this subject)");
            SimOutput.warnings(loaded.warnings(), world.stubbedTags());
            return;
        }

        int kept = 0;
        for (Beat beat : beats) {
            if (beat.retained()) kept++;
            System.out.printf(Locale.ROOT, "  age %-3d %-7s %-20s %-52s mag %.2f  rel %5.1f  %s%n",
                    beat.ageYears(),
                    subject.ageBands().stageAt(beat.ageYears()).name().toLowerCase(Locale.ROOT),
                    beat.templateId().getPath(), beat.headline(),
                    beat.magnitude(), beat.relevance(),
                    beat.retained() ? "kept" : "forgotten");
        }

        System.out.printf(Locale.ROOT, "%n%d beats, %d kept, %d below the threshold%n",
                beats.size(), kept, beats.size() - kept);

        Map<UUID, String> names = new HashMap<>();
        others.forEach(other -> names.put(other.uuid(), other.displayName()));
        List<VillagerMemory> memories = world.memories(uuid);
        SimOutput.section(String.format(Locale.ROOT,
                "what they carry (%d memories, %d accounts)", memories.size(),
                world.knownStories(uuid)));
        for (VillagerMemory memory : memories) {
            String about = memory.otherParty() == null ? ""
                    : "  about " + names.getOrDefault(memory.otherParty(), "someone");
            System.out.printf(Locale.ROOT, "  %-40s strength %5.2f  valence %+.2f  recalled %d%s%n",
                    memory.memoryKey(), memory.strength(), memory.valence(), memory.count(), about);
        }

        Map<String, Integer> counters = world.counters(uuid);
        if (!counters.isEmpty()) {
            SimOutput.section("what a life of work adds up to (the background summary)");
            counters.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> System.out.printf(Locale.ROOT, "  %-32s %,8d%n",
                            e.getKey(), e.getValue()));
        }

        System.out.printf(Locale.ROOT, "%ntruth written: %d events, %d digest entries%n",
                world.events().size(), 0);
        System.out.println("(a personal past is belief only: no truth rows, nothing for mechanics to read)");

        SimOutput.warnings(loaded.warnings(), world.stubbedTags());
    }
}
