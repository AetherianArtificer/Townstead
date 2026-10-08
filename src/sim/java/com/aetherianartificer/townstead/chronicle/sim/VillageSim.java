package com.aetherianartificer.townstead.chronicle.sim;

import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import com.aetherianartificer.townstead.chronicle.model.Arc;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.ChronicleRef;
import com.aetherianartificer.townstead.chronicle.model.Participation;
import com.aetherianartificer.townstead.chronicle.model.VillageHistory;
import com.aetherianartificer.townstead.chronicle.model.VillagerMemory;
import com.aetherianartificer.townstead.chronicle.pregen.ChroniclePregen;
import com.aetherianartificer.townstead.chronicle.scope.ChronicleScopes;
import com.aetherianartificer.townstead.chronicle.scope.ScopeProfile;
import com.aetherianartificer.townstead.chronicle.scope.ScopeRelevance;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.aetherianartificer.townstead.chronicle.world.ChronicleSubject;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Fabricates a village's history and prints it. */
public final class VillageSim {

    private VillageSim() {}

    public static int run(SimArgs args, SimTemplates.Loaded loaded) {
        int villageId = args.number("village", 1);
        int years = args.number("years", 200);
        int daysPerYear = args.number("days-per-year", 360);
        long seed = args.number("seed", 1L);
        boolean playerFounded = args.has("player-founded");
        ScopeProfile scope = ChronicleScopes.VILLAGE
                .withThreshold(args.decimal("threshold", ChronicleScopes.VILLAGE.threshold()));

        long today = (long) years * daysPerYear;
        RandomSource rng = RandomSource.create(seed);
        SimChronicleWorld world = new SimChronicleWorld(
                loaded.templates(), today, daysPerYear, rng, args.itemTags());
        List<ChronicleSubject> residents = SimRoster.of(args.number("residents", 12), rng);
        VillageKey key = new VillageKey(args.id("dim", "minecraft:overworld"), villageId);

        ChroniclePregen.generate(world, key, 0L, playerFounded, residents);

        print(world, key, residents, loaded, years, daysPerYear, seed, playerFounded, scope);

        List<String> violations = SimChecks.run(world.events(), world.arcs(), 0L, today,
                world.history(key).entries().size());
        SimOutput.violations(violations);
        return args.has("check") && !violations.isEmpty() ? 1 : 0;
    }

    private static void print(SimChronicleWorld world, VillageKey key,
                              List<ChronicleSubject> residents, SimTemplates.Loaded loaded,
                              int years, int daysPerYear, long seed, boolean playerFounded,
                              ScopeProfile scope) {
        List<ChronicleEvent> events = world.events();
        SimOutput.heading(String.format(Locale.ROOT,
                "village %d in %s | %d years of history | %d days/year | seed %d%s",
                key.villageId(), key.dimension(), years, daysPerYear, seed,
                playerFounded ? " | player-founded" : ""));
        System.out.printf(Locale.ROOT, "%d templates loaded, %d in the pre-history pool, %d residents%n%n",
                loaded.templates().size(), pregenPoolSize(loaded), residents.size());

        int scopeKept = 0;
        Map<String, Integer> scopeDropped = new TreeMap<>();
        for (ChronicleEvent event : events) {
            ChronicleEventTemplate template = world.template(event.templateId());
            float relevance = template == null ? 0f
                    : ScopeRelevance.forVillage(template, event.magnitude(), true);
            boolean retains = scope.retains(relevance);
            if (retains) {
                scopeKept++;
            } else {
                scopeDropped.merge(event.templateId().getPath(), 1, Integer::sum);
            }
            System.out.printf(Locale.ROOT, "  y%-4d d%-6d %-20s %-52s rel %5.1f  %s%s%n",
                    event.worldDay() / daysPerYear,
                    event.worldDay() % daysPerYear,
                    event.templateId().getPath(),
                    template == null ? "" : template.headline(event.params()),
                    relevance,
                    retains ? "digest " : "passing",
                    boundResidents(event) ? "  [living residents]" : "");
        }

        VillageHistory history = world.history(key);
        System.out.printf(Locale.ROOT, "%n%d events, %d ancestors%n",
                events.size(), world.concepts().size());
        for (Arc arc : world.arcs()) {
            System.out.printf(Locale.ROOT, "  arc %s#%d  d%d..d%d  %s%n", arc.type(), arc.arcId(),
                    arc.startDay(), arc.endDay(),
                    arc.status() == Arc.STATUS_CLOSED ? "closed" : "OPEN");
        }

        SimOutput.section("digest");
        System.out.printf(Locale.ROOT, "  template keep flag: %d entries%n", history.entries().size());
        System.out.printf(Locale.ROOT, "  village scope (threshold %.1f): %d entries%n",
                scope.threshold(), scopeKept);
        if (!scopeDropped.isEmpty()) {
            System.out.printf(Locale.ROOT, "  scope would drop: %s%n", scopeDropped);
        }
        history.counts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.printf(Locale.ROOT, "  %-24s %d%n", e.getKey(), e.getValue()));

        SimOutput.section("seeded elder memories");
        boolean any = false;
        for (ChronicleSubject resident : residents) {
            List<VillagerMemory> memories = world.memories(resident.uuid());
            if (memories.isEmpty()) continue;
            any = true;
            System.out.printf(Locale.ROOT, "  %s (%d accounts)%n",
                    resident.displayName(), world.knownStories(resident.uuid()));
            for (VillagerMemory memory : memories) {
                System.out.printf(Locale.ROOT, "    %-40s strength %.2f  valence %+.2f%n",
                        memory.memoryKey(), memory.strength(), memory.valence());
            }
        }
        if (!any) System.out.println("  (none: no bindable events inside the memory window)");

        SimOutput.warnings(loaded.warnings(), world.stubbedTags());
    }

    private static int pregenPoolSize(SimTemplates.Loaded loaded) {
        int pool = 0;
        for (ChronicleEventTemplate template : loaded.templates().values()) {
            if (template.contexts().contains(ChronicleEventTemplate.Context.PREGEN)) pool++;
        }
        return pool;
    }

    /** True when at least one role bound a living resident rather than a fabricated ancestor. */
    private static boolean boundResidents(ChronicleEvent event) {
        for (Participation participation : event.participations()) {
            if (participation.ref().kind() == ChronicleRef.Kind.VILLAGER) return true;
        }
        return false;
    }
}
