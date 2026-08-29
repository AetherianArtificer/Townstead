package com.aetherianartificer.townstead.chronicle.sim;

import com.aetherianartificer.townstead.chronicle.model.Arc;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.ChronicleRef;
import com.aetherianartificer.townstead.chronicle.model.Participation;
import com.aetherianartificer.townstead.chronicle.pregen.ChroniclePersonalPregen;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.aetherianartificer.townstead.social.BondKinds;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Invariants a fabricated history should hold. These live here rather than in
 * a JUnit test because the test source set shadows CompoundTag and BlockPos
 * with stubs, which the hot tier cannot load against; {@code --check} makes the
 * harness itself the regression gate.
 */
public final class SimChecks {

    private SimChecks() {}

    public static List<String> run(List<ChronicleEvent> events, List<Arc> arcs,
                                   long birthDay, long today, int digestEntries) {
        List<String> violations = new ArrayList<>();
        long previousDay = Long.MIN_VALUE;
        for (ChronicleEvent event : events) {
            if (event.worldDay() < birthDay) {
                violations.add(String.format(Locale.ROOT,
                        "event %d (%s) predates the founding: d%d < d%d",
                        event.eventId(), event.templateId(), event.worldDay(), birthDay));
            }
            if (event.worldDay() >= today) {
                violations.add(String.format(Locale.ROOT,
                        "event %d (%s) is not in the past: d%d >= today d%d",
                        event.eventId(), event.templateId(), event.worldDay(), today));
            }
            if (event.worldDay() < previousDay) {
                violations.add(String.format(Locale.ROOT,
                        "event %d (%s) is out of order: d%d after d%d",
                        event.eventId(), event.templateId(), event.worldDay(), previousDay));
            }
            previousDay = event.worldDay();
            duplicateRoleBinding(event, violations);
        }
        for (Arc arc : arcs) {
            if (arc.status() != Arc.STATUS_CLOSED) {
                violations.add("arc " + arc.type() + "#" + arc.arcId() + " was left open");
            }
        }
        if (digestEntries != events.size()) {
            violations.add(String.format(Locale.ROOT,
                    "digest holds %d entries for %d events", digestEntries, events.size()));
        }
        return violations;
    }

    /** A life runs forwards, stays inside its own span, and honours per-life limits. */
    public static List<String> runLife(List<ChroniclePersonalPregen.Beat> beats, int age,
                                       Map<ResourceLocation, ChronicleEventTemplate> templates) {
        List<String> violations = new ArrayList<>();
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        Map<String, Set<String>> bondPartners = new HashMap<>();
        long previousDay = Long.MIN_VALUE;
        for (ChroniclePersonalPregen.Beat beat : beats) {
            if (beat.worldDay() < previousDay) {
                violations.add(String.format(Locale.ROOT, "beat %s is out of order at d%d",
                        beat.templateId(), beat.worldDay()));
            }
            previousDay = beat.worldDay();
            if (beat.ageYears() < 0 || beat.ageYears() > age) {
                violations.add(String.format(Locale.ROOT,
                        "beat %s happens at age %d, outside a life of %d years",
                        beat.templateId(), beat.ageYears(), age));
            }
            int seen = counts.merge(beat.templateId(), 1, Integer::sum);
            ChronicleEventTemplate template = templates.get(beat.templateId());
            if (template != null && template.maxPerLife() > 0 && seen > template.maxPerLife()) {
                violations.add(String.format(Locale.ROOT,
                        "%s happens %d times in one life, past its max_per_life of %d",
                        beat.templateId(), seen, template.maxPerLife()));
            }
            if (template != null && template.primaryRole().fatal()) {
                violations.add(beat.templateId() + " binds the subject to a fatal role");
            }
            // The same tie must not form twice with the same person: two people
            // become fast friends once, not every few years.
            ChronicleEventTemplate.PregenBond bond =
                    template == null ? null : template.pregenBond();
            if (bond != null && !bond.ends() && BondKinds.byId(bond.kind()).uniquePerPair()) {
                String partner = beat.params().get(bond.withRole());
                if (partner != null && !bondPartners
                        .computeIfAbsent(bond.kind(), ignored -> new HashSet<>()).add(partner)) {
                    violations.add(String.format(Locale.ROOT,
                            "%s forms a %s with %s more than once",
                            beat.templateId(), bond.kind(), partner));
                }
            }
        }
        return violations;
    }

    /** One person cannot be both sides of a wedding or an argument. */
    private static void duplicateRoleBinding(ChronicleEvent event, List<String> violations) {
        Set<String> seen = new HashSet<>();
        for (Participation participation : event.participations()) {
            if (Participation.ROLE_WITNESS.equals(participation.role())) continue;
            ChronicleRef ref = participation.ref();
            String identity = ref.uuid() != null ? ref.uuid().toString() : String.valueOf(ref.str());
            if (!seen.add(identity)) {
                violations.add(String.format(Locale.ROOT,
                        "event %d (%s) bound %s to more than one role",
                        event.eventId(), event.templateId(), ref.displayName()));
            }
        }
    }
}
