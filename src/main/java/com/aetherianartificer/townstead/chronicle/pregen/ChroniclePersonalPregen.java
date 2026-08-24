package com.aetherianartificer.townstead.chronicle.pregen;

import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.ChronicleRef;
import com.aetherianartificer.townstead.chronicle.model.Participation;
import com.aetherianartificer.townstead.chronicle.scope.ChronicleScopes;
import com.aetherianartificer.townstead.chronicle.scope.ScopeProfile;
import com.aetherianartificer.townstead.chronicle.scope.ScopeRelevance;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.aetherianartificer.townstead.chronicle.world.ChronicleSubject;
import com.aetherianartificer.townstead.chronicle.world.ChronicleWorld;
import com.aetherianartificer.townstead.chronicle.world.ServerChronicleWorld;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.root.CanonicalStage;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.social.Bond;
import com.aetherianartificer.townstead.social.BondKind;
import com.aetherianartificer.townstead.social.BondKinds;
import com.aetherianartificer.townstead.reaction.WeightedPicker;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Fabricates one person's past: the things they did, the people they did them
 * with, and what stuck. Deterministic per subject uuid, so a life generated on
 * first sight is the same life whenever it is asked for again, which is what
 * makes generating it lazily safe.
 *
 * <p>Belief tier only. A personal past writes accounts and memories and never
 * calls {@code record}, so it produces no truth rows and no digest lines: the
 * firewall holds, because nothing mechanical may read what this fabricates.
 * Retention is the person scope's decision, not this generator's — a beat below
 * their threshold happened and was forgotten, and the harness can show you both.</p>
 *
 * <p>It fabricates only around facts other systems own. Family, age and
 * profession come from the subject; this invents who they worked beside and
 * what they carry from it.</p>
 */
public final class ChroniclePersonalPregen {

    private static final long SEED_MIX = 0x11FE10DEL;
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;
    private static final int MAX_BEATS = 24;
    private static final int FIRST_BEAT_AGE_YEARS = 4;
    /** Your own life, first-hand. */
    private static final float SELF_FIDELITY = 1.0f;

    /**
     * One moment in a fabricated life. {@code retained} is whether the person
     * kept it; the rest is reported either way so thresholds can be judged.
     */
    public record Beat(long worldDay, int ageYears, ResourceLocation templateId, String headline,
                       String role, float magnitude, float relevance, boolean retained,
                       Map<String, String> params) {}

    private ChroniclePersonalPregen() {}

    public static List<Beat> generate(MinecraftServer server, VillagerEntityMCA villager,
                                      long birthDay, VillageKey village,
                                      List<VillagerEntityMCA> acquaintances) {
        List<ChronicleSubject> subject = ServerChronicleWorld.subjects(List.of(villager));
        return generate(new ServerChronicleWorld(server), subject.get(0), birthDay, village,
                ServerChronicleWorld.subjects(acquaintances), ChronicleScopes.PERSON);
    }

    public static List<Beat> generate(ChronicleWorld world, ChronicleSubject subject, long birthDay,
                                      VillageKey village, List<ChronicleSubject> others,
                                      ScopeProfile scope) {
        List<Beat> beats = new ArrayList<>();
        long today = world.today();
        int dpy = world.daysPerYear();
        if (today <= birthDay || dpy <= 0) return beats;

        RandomSource rng = RandomSource.create(subject.uuid().getMostSignificantBits()
                ^ subject.uuid().getLeastSignificantBits() * GOLDEN ^ SEED_MIX);

        Map<ResourceLocation, Map<String, List<ResourceLocation>>> paramPools = new HashMap<>();
        List<ChronicleEventTemplate> pool = candidates(world, subject, paramPools);
        if (pool.isEmpty()) return beats;

        Map<ResourceLocation, Integer> lifetimeCounts = new HashMap<>();
        // The counters this life would have accumulated, keyed like the live taps,
        // so a milestone gate ("their first meal") means the same thing here.
        Map<String, Integer> counters = new HashMap<>();
        Map<String, Integer> perYear = ChronicleWorkHistories.perYear(subject.professionId());
        List<Bond> bonds = new ArrayList<>();
        int workedYears = 0;
        long day = birthDay + (long) FIRST_BEAT_AGE_YEARS * dpy;
        while (beats.size() < MAX_BEATS && day < today) {
            day += (long) (1 + rng.nextInt(3)) * dpy - rng.nextInt(dpy);
            if (day >= today) break;
            int age = (int) ((day - birthDay) / dpy);
            Optional<ChronicleEventTemplate> picked =
                    WeightedPicker.pick(pool, ChronicleEventTemplate::pickWeight, rng);
            if (picked.isEmpty()) break;
            ChronicleEventTemplate template = picked.get();

            // The role's own pheno gate, asked about who they were then.
            String counterKey = template.trigger() == null ? null : template.trigger().key();
            Condition gate = template.primaryRole().when();
            if (gate != null && !gate.test(new ConditionContext(
                    new LifeSubject(subject, age, bonds, counters, counterKey)))) {
                continue;
            }
            if (template.maxPerLife() > 0
                    && lifetimeCounts.getOrDefault(template.id(), 0) >= template.maxPerLife()) {
                continue;
            }
            lifetimeCounts.merge(template.id(), 1, Integer::sum);
            if (counterKey != null) counters.merge(counterKey, 1, Integer::sum);
            // The trade's ordinary volume accrues after the beat is judged, so the
            // first meal is tested as a first and the years that follow are not.
            workedYears += accrueWorkHistory(counters, perYear, subject, age, workedYears);

            Beat beat = beat(world, subject, village, others, template,
                    paramPools.get(template.id()), day, age, scope, rng, bonds);
            beats.add(beat);
        }

        // A background is a summary, not a replay: what is written is the work history
        // the years imply, never an invented event.
        int adultYears = Math.max(0, (int) ((today - birthDay) / dpy) - adultAge(subject));
        for (Map.Entry<String, Integer> rate : perYear.entrySet()) {
            int total = rate.getValue() * adultYears;
            if (total > 0) world.addCounter(subject.uuid(), rate.getKey(), total);
        }
        return beats;
    }

    /** Years of the trade that have passed since the last beat, once they are grown. */
    private static int accrueWorkHistory(Map<String, Integer> counters,
                                         Map<String, Integer> perYear,
                                         ChronicleSubject subject, int age, int workedYears) {
        int grownFor = Math.max(0, age - adultAge(subject));
        int newYears = grownFor - workedYears;
        if (newYears <= 0 || perYear.isEmpty()) return 0;
        for (Map.Entry<String, Integer> rate : perYear.entrySet()) {
            counters.merge(rate.getKey(), rate.getValue() * newYears, Integer::sum);
        }
        return newYears;
    }

    /** The age this subject's own Root calls adult, where a trade can begin. */
    private static int adultAge(ChronicleSubject subject) {
        for (int years = 0; years <= 120; years++) {
            CanonicalStage stage = subject.ageBands().stageAt(years);
            if (stage == CanonicalStage.ADULT || stage == CanonicalStage.SENIOR) return years;
        }
        return 18;
    }

    private static Beat beat(ChronicleWorld world, ChronicleSubject subject, VillageKey village,
                             List<ChronicleSubject> others, ChronicleEventTemplate template,
                             @Nullable Map<String, List<ResourceLocation>> pools, long day, int age,
                             ScopeProfile scope, RandomSource rng, List<Bond> bonds) {
        String primaryRole = template.primaryRole().id();
        List<Participation> participations = new ArrayList<>();
        Map<String, String> params = new HashMap<>();
        participations.add(new Participation(primaryRole, subject.ref()));
        params.put(primaryRole, subject.displayName());

        ChronicleEventTemplate.PregenBond bondSpec = template.pregenBond();
        for (int i = 1; i < template.roles().size(); i++) {
            ChronicleEventTemplate.RoleSpec role = template.roles().get(i);
            ChronicleRef ref = role.fromBond() != null
                    ? partnerIn(bonds, role.fromBond()) : null;
            if (ref == null) {
                // When the kind says a pair may only form it once, this role cannot go
                // to someone the subject already holds that bond with.
                Set<UUID> alreadyTied = bondSpec != null && !bondSpec.ends()
                        && bondSpec.withRole().equals(role.id())
                        && BondKinds.byId(bondSpec.kind()).uniquePerPair()
                        ? tiedTo(bonds, bondSpec.kind()) : Set.of();
                ref = PregenPeople.bindResident(others, participations, role, rng, alreadyTied);
            }
            if (ref == null) {
                ref = PregenPeople.fabricate(
                        world, village, PregenPeople.KIND_ACQUAINTANCE, day, rng);
            }
            participations.add(new Participation(role.id(), ref));
            params.put(role.id(), ref.displayName());
        }
        PregenParams.fill(world, params, pools, rng);
        formBond(template, participations, bonds, day);

        float magnitude = PregenMagnitude.draw(rng, template);
        ChronicleEvent fabricated = new ChronicleEvent(
                ChronicleEvent.NONE, template.id(), day, 0L, village.dimension(), 0L,
                village.villageId(), template.category(), magnitude, template.reach(),
                ChronicleEvent.NONE, ChronicleEvent.NONE, false, participations, params);

        float relevance = ScopeRelevance.forPerson(template, magnitude, primaryRole, true);
        boolean retained = scope.retains(relevance);
        if (retained) {
            PregenMemories.remember(world, template, fabricated, subject.uuid(), primaryRole,
                    SELF_FIDELITY, day);
        }
        return new Beat(day, age, template.id(), template.headline(params), primaryRole,
                magnitude, relevance, retained, params);
    }

    /** Templates this person could have been at the centre of. */
    private static List<ChronicleEventTemplate> candidates(
            ChronicleWorld world, ChronicleSubject subject,
            Map<ResourceLocation, Map<String, List<ResourceLocation>>> paramPools) {
        List<ChronicleEventTemplate> pool = new ArrayList<>();
        for (ChronicleEventTemplate template : world.templates().values()) {
            if (!template.contexts().contains(ChronicleEventTemplate.Context.PREGEN)) continue;
            ChronicleEventTemplate.RoleSpec primary = template.primaryRole();
            if (!primary.kind().isPerson() || !subject.kind().isPerson()) continue;
            if (primary.fatal()) continue;
            // A gate pre-history cannot answer must not run ungated: drop the template.
            if (primary.when() != null && !primary.when().supportsSubject()) continue;
            Map<String, List<ResourceLocation>> resolved = PregenParams.resolve(template, world);
            if (resolved == null) continue;
            paramPools.put(template.id(), resolved);
            pool.add(template);
        }
        return pool;
    }

    /** Everyone the subject already holds a bond of this kind with. */
    private static Set<UUID> tiedTo(List<Bond> bonds, String kind) {
        Set<UUID> tied = new HashSet<>();
        for (Bond bond : bonds) {
            if (bond.kind().equals(kind) && bond.other() != null) tied.add(bond.other());
        }
        return tied;
    }

    /** Whoever the subject currently holds a bond of this kind with, if anyone. */
    private static @Nullable ChronicleRef partnerIn(List<Bond> bonds, String kind) {
        for (Bond bond : bonds) {
            if (!bond.kind().equals(kind) || !bond.active()) continue;
            return bond.other() == null
                    ? ChronicleRef.concept("bond:" + kind, bond.otherName())
                    : ChronicleRef.villager(bond.other(), bond.otherName());
        }
        return null;
    }

    /** The tie this event leaves behind, or takes away, so later beats can ask about it. */
    private static void formBond(ChronicleEventTemplate template, List<Participation> participations,
                                 List<Bond> bonds, long day) {
        ChronicleEventTemplate.PregenBond spec = template.pregenBond();
        if (spec == null) return;
        for (Participation participation : participations) {
            if (!participation.role().equals(spec.withRole())) continue;
            ChronicleRef other = participation.ref();
            if (spec.ends()) {
                endBond(bonds, spec.kind(), other, day);
            } else {
                BondKind kind = BondKinds.byId(spec.kind());
                if (kind.unlimited() || countActive(bonds, spec.kind()) < kind.maxActive()) {
                    bonds.add(Bond.ongoing(spec.kind(), other.uuid(), other.displayName(), day));
                }
            }
            return;
        }
    }

    /** A bond that ended is kept, ended: widowed and never married are different states. */
    private static void endBond(List<Bond> bonds, String kind, ChronicleRef other, long day) {
        for (int i = 0; i < bonds.size(); i++) {
            Bond bond = bonds.get(i);
            if (!bond.kind().equals(kind) || !bond.active()) continue;
            boolean samePerson = bond.other() != null && bond.other().equals(other.uuid());
            if (samePerson || bond.otherName().equals(other.displayName())) {
                bonds.set(i, bond.ended(day));
                return;
            }
        }
    }

    private static int countActive(List<Bond> bonds, String kind) {
        int count = 0;
        for (Bond bond : bonds) {
            if (bond.kind().equals(kind) && bond.active()) count++;
        }
        return count;
    }

    /** Seed helper for callers that want the same life without generating it. */
    public static long seedFor(UUID subject) {
        return subject.getMostSignificantBits()
                ^ subject.getLeastSignificantBits() * GOLDEN ^ SEED_MIX;
    }
}
