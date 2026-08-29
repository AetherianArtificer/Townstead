package com.aetherianartificer.townstead.chronicle.sim;

import com.aetherianartificer.townstead.chronicle.model.Participation;
import com.aetherianartificer.townstead.chronicle.scope.ChronicleScopes;
import com.aetherianartificer.townstead.chronicle.scope.ScopeProfile;
import com.aetherianartificer.townstead.chronicle.scope.ScopeRelevance;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Every template on one scale, so "how big a deal is this" can be judged across
 * the set instead of one file at a time. Significance is what the scopes read;
 * pick weight is how often it happens. They are different questions and this
 * view puts them side by side, because a template's rarity currently drives both.
 */
public final class ScaleSim {

    private ScaleSim() {}

    public static int run(SimArgs args, SimTemplates.Loaded loaded) {
        ScopeProfile person = ChronicleScopes.PERSON
                .withThreshold(args.decimal("person-threshold", ChronicleScopes.PERSON.threshold()));
        ScopeProfile village = ChronicleScopes.VILLAGE
                .withThreshold(args.decimal("threshold", ChronicleScopes.VILLAGE.threshold()));
        float magnitude = args.decimal("magnitude", 1.0f);

        SimOutput.heading(String.format(Locale.ROOT,
                "%d templates at magnitude %.2f | person keeps at %.1f | village keeps at %.1f",
                loaded.templates().size(), magnitude, person.threshold(), village.threshold()));
        System.out.printf(Locale.ROOT, "%-24s %5s %-10s %6s   %7s  %-28s %s%n",
                "template", "news", "rarity", "signif", "pick wt", "person (own/saw/heard)", "village");

        List<ChronicleEventTemplate> sorted = new ArrayList<>(loaded.templates().values());
        sorted.sort(Comparator.comparingDouble(
                (ChronicleEventTemplate t) -> ScopeRelevance.significance(t, magnitude)).reversed());

        for (ChronicleEventTemplate template : sorted) {
            float significance = ScopeRelevance.significance(template, magnitude);
            float own = ScopeRelevance.forPerson(template, magnitude, template.primaryRole().id(), true);
            float saw = ScopeRelevance.forPerson(template, magnitude, Participation.ROLE_WITNESS, true);
            float heard = ScopeRelevance.forPerson(template, magnitude, null, true);
            float villageRelevance = ScopeRelevance.forVillage(template, magnitude, true);
            System.out.printf(Locale.ROOT,
                    "%-24s %5.1f %-10s %6.1f   %7.1f  %-28s %s%n",
                    template.id().getPath(), template.newsValue(),
                    template.rarity().name().toLowerCase(Locale.ROOT), significance,
                    template.pickWeight(),
                    String.format(Locale.ROOT, "%s %s %s",
                            mark(person, own), mark(person, saw), mark(person, heard)),
                    String.format(Locale.ROOT, "%5.1f %s", villageRelevance,
                            village.retains(villageRelevance) ? "digest" : "passing"));
        }

        SimOutput.section("reading this");
        System.out.println("  significance = news_value x magnitude. Rarity is frequency only:");
        System.out.println("  it moves pick weight and nothing else.");
        System.out.println("  Anchors: 1 private, 3 the village notices, 5 remembered for years,");
        System.out.println("  10 a generation remembers. A * means that scope keeps it.");
        return 0;
    }

    private static String mark(ScopeProfile scope, float relevance) {
        return String.format(Locale.ROOT, "%5.1f%s", relevance, scope.retains(relevance) ? "*" : " ");
    }
}
