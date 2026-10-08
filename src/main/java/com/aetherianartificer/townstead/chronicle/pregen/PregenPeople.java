package com.aetherianartificer.townstead.chronicle.pregen;

import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import com.aetherianartificer.townstead.chronicle.concept.ConceptLedger;
import com.aetherianartificer.townstead.chronicle.model.ChronicleRef;
import com.aetherianartificer.townstead.chronicle.model.Participation;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.aetherianartificer.townstead.chronicle.world.ChronicleSubject;
import com.aetherianartificer.townstead.chronicle.world.ChronicleWorld;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * People a fabricated past needs but the world does not contain: the founder
 * nobody met, the master who taught someone their trade. They exist as concepts
 * in the {@link ConceptLedger}, never as entities, and their names are captured
 * once so the story stays readable forever.
 */
public final class PregenPeople {

    public static final String KIND_ANCESTOR = "ancestor";
    public static final String KIND_ACQUAINTANCE = "acquaintance";

    private PregenPeople() {}

    public static ChronicleRef fabricate(ChronicleWorld world, VillageKey village, String kind,
                                         long day, RandomSource rng) {
        UUID id = new UUID(rng.nextLong(), rng.nextLong());
        String name = world.fabricateName(rng, id);
        String conceptId = kind + ":" + id;
        world.putConcept(new ConceptLedger.ConceptEntry(conceptId, kind, name, "", day, village));
        return ChronicleRef.concept(conceptId, name);
    }

    /** True when this ref already holds a role in the event being built. */
    public static boolean isBound(List<Participation> alreadyBound, ChronicleRef candidate) {
        for (Participation participation : alreadyBound) {
            ChronicleRef bound = participation.ref();
            if (bound.uuid() != null && bound.uuid().equals(candidate.uuid())) return true;
            if (bound.str() != null && bound.str().equals(candidate.str())) return true;
        }
        return false;
    }

    /**
     * Binds a living villager to a role, honouring the role's declarative
     * pre-history filter. Null when nobody present fits.
     *
     * <p>The scan starts at a random offset rather than the top of the roster:
     * scanning in order gave the same neighbour every role in every event, so a
     * fabricated life read as one relationship repeated for forty years.</p>
     */
    public static @Nullable ChronicleRef bindResident(List<ChronicleSubject> residents,
                                                      List<Participation> alreadyBound,
                                                      ChronicleEventTemplate.RoleSpec role,
                                                      RandomSource rng) {
        return bindResident(residents, alreadyBound, role, rng, Set.of());
    }

    /**
     * As above, skipping {@code excluded}. Callers pass the people the subject
     * already holds the event's bond with: you cannot become fast friends with
     * the same person twice, so the role finds someone else or the story invents
     * a stranger.
     */
    public static @Nullable ChronicleRef bindResident(List<ChronicleSubject> residents,
                                                      List<Participation> alreadyBound,
                                                      ChronicleEventTemplate.RoleSpec role,
                                                      RandomSource rng, Set<UUID> excluded) {
        if (!role.kind().isPerson() || residents.isEmpty()) return null;
        Condition gate = role.when();
        if (gate != null && !gate.supportsSubject()) return null;
        int start = rng.nextInt(residents.size());
        for (int i = 0; i < residents.size(); i++) {
            ChronicleSubject resident = residents.get((start + i) % residents.size());
            if (resident.baby()) continue;
            if (excluded.contains(resident.uuid())) continue;
            if (isBound(alreadyBound, resident.ref())) continue;
            if (gate != null
                    && !gate.test(new ConditionContext(LifeSubject.grown(resident)))) {
                continue;
            }
            return resident.ref();
        }
        return null;
    }
}
