package com.aetherianartificer.townstead.pheno.combat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Ward selection: wards never stack, the one that spares the most takes the hit, budgets cap it. */
class WardsTest {

    @Test
    void largestEligibleSavingWins() {
        List<Wards.Candidate> wards = List.of(
                new Wards.Candidate(4f, 1f, true),     // spares 4 of 10
                new Wards.Candidate(12f, 0.5f, true),  // spares 5 of 10
                new Wards.Candidate(20f, 1f, false));  // would spare 10, but the damage does not match
        assertEquals(1, Wards.best(wards, 10f));
        assertEquals(5f, wards.get(1).spares(10f));
    }

    @Test
    void budgetCapsWhatIsSpared() {
        Wards.Candidate ward = new Wards.Candidate(3f, 1f, true);
        assertEquals(3f, ward.spares(8f));
        assertEquals(2f, ward.spares(2f));
    }

    @Test
    void nothingEligibleSparesNothing() {
        assertEquals(-1, Wards.best(List.of(new Wards.Candidate(5f, 1f, false)), 4f));
        assertEquals(-1, Wards.best(List.of(new Wards.Candidate(0f, 1f, true)), 4f));
        assertEquals(-1, Wards.best(List.of(), 4f));
    }

    @Test
    void tieKeepsTheFirstWard() {
        List<Wards.Candidate> wards = List.of(new Wards.Candidate(4f, 1f, true), new Wards.Candidate(4f, 1f, true));
        assertEquals(0, Wards.best(wards, 6f));
    }
}
