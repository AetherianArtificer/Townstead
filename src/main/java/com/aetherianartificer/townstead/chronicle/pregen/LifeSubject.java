package com.aetherianartificer.townstead.chronicle.pregen;

import com.aetherianartificer.townstead.chronicle.world.ChronicleSubject;
import com.aetherianartificer.townstead.pheno.condition.PhenoSubject;
import com.aetherianartificer.townstead.root.CanonicalStage;
import com.aetherianartificer.townstead.social.Bond;
import com.aetherianartificer.townstead.social.Bonds;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Someone as they were at one moment of a fabricated past: the same person,
 * at the age they were then, holding the ties they had formed by then. This is
 * what pheno conditions are evaluated against during generation, so a role
 * gated on {@code pheno:life_stage} or {@code pheno:bonds} answers about the
 * younger self rather than about who they are today.
 */
public record LifeSubject(ChronicleSubject subject, int ageYears, List<Bond> ties,
                          Map<String, Integer> counters, @Nullable String pendingKey)
        implements PhenoSubject {

    public LifeSubject {
        ties = ties == null ? List.of() : List.copyOf(ties);
        counters = counters == null ? Map.of() : counters;
    }

    public LifeSubject(ChronicleSubject subject, int ageYears, List<Bond> ties) {
        this(subject, ageYears, ties, Map.of(), null);
    }

    /**
     * The tally including the occurrence being considered, because live taps count
     * before they offer the trigger: a first meal is tested as count 1, not 0.
     */
    @Override
    public int counter(String key) {
        int held = counters.getOrDefault(key, 0);
        return key.equals(pendingKey) ? held + 1 : held;
    }

    /** A stand-in for someone whose history we are not tracking: an adult with no known ties. */
    public static LifeSubject grown(ChronicleSubject subject) {
        return new LifeSubject(subject, ADULT_ENOUGH, List.of(), Map.of(), null);
    }

    private static final int ADULT_ENOUGH = 30;

    @Override
    public UUID uuid() {
        return subject.uuid();
    }

    @Override
    public String displayName() {
        return subject.displayName();
    }

    @Override
    public String professionId() {
        return subject.professionId();
    }

    @Override
    public CanonicalStage lifeStage() {
        return subject.ageBands().stageAt(ageYears);
    }

    @Override
    public Bonds bonds() {
        return Bonds.of(ties);
    }
}
