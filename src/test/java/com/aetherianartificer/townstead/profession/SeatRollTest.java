package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.profession.def.JobSiteProvider;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProgressionTrack;
import com.aetherianartificer.townstead.profession.def.RetrainingPolicy;
import com.aetherianartificer.townstead.profession.def.UnlockModel;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Job stability: a worker keeps a seat until there is proof they left it. */
class SeatRollTest {

    private static final ResourceLocation COOK = ResourceLocation.tryParse("townstead:cook");
    private static final JobSiteProvider.Building KITCHENS = new JobSiteProvider.Building(
            List.of("compat/farmersdelight/kitchen_l"), List.of(1, 1, 2, 2, 3));
    private static final List<JobSiteProvider> PROVIDERS = List.of(KITCHENS);

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    @Test
    void unloadedWorkerKeepsTheSeat() {
        SeatRoll roll = new SeatRoll();
        roll.enroll(A, COOK, null, 100);

        // A's chunk unloads. B is loaded and idle.
        roll.sync(Map.of(), Set.of(B), Set.of(A, B));

        assertTrue(roll.holds(A));
        assertEquals(-1, roll.seatIfHired(B, COOK, null, seats(1), PROVIDERS),
                "an unloaded worker is not a vacancy");
    }

    @Test
    void loadedWorkerWhoTookOtherWorkFreesTheSeat() {
        SeatRoll roll = new SeatRoll();
        roll.enroll(A, COOK, null, 100);

        roll.sync(Map.of(), Set.of(A, B), Set.of(A, B));

        assertFalse(roll.holds(A));
        assertEquals(0, roll.seatIfHired(B, COOK, null, seats(1), PROVIDERS));
    }

    @Test
    void workerOffTheBooksFreesTheSeat() {
        SeatRoll roll = new SeatRoll();
        roll.enroll(A, COOK, null, 100);

        roll.sync(Map.of(), Set.of(B), Set.of(B));

        assertFalse(roll.holds(A));
    }

    @Test
    void newcomerNeverDisplacesAnIncumbent() {
        SeatRoll roll = new SeatRoll();
        roll.enroll(C, COOK, null, 100);
        roll.enroll(B, COOK, null, 200);

        // A sorts first by id, but arrives last.
        assertEquals(-1, roll.seatIfHired(A, COOK, null, seats(2), PROVIDERS));
        assertEquals(0, roll.seatOf(C, seats(2), PROVIDERS));
        assertEquals(1, roll.seatOf(B, seats(2), PROVIDERS));
    }

    @Test
    void reloadingAWorkerKeepsTheirSeniority() {
        SeatRoll roll = new SeatRoll();
        roll.enroll(A, COOK, null, 100);
        roll.enroll(B, COOK, null, 200);

        // A unloads, then loads again reporting a later seat time.
        roll.sync(Map.of(B, present(200)), Set.of(B), Set.of(A, B));
        roll.sync(Map.of(A, present(900), B, present(200)), Set.of(A, B), Set.of(A, B));

        assertEquals(0, roll.seatOf(A, seats(2), PROVIDERS));
        assertEquals(1, roll.seatOf(B, seats(2), PROVIDERS));
    }

    @Test
    void nobodyLosesAJobWhileChunksLoadAndUnload() {
        Churn churn = simulate(true);
        assertEquals(0, churn.releases, "no worker may be released by chunk loading alone");
        assertEquals(3, churn.employed, "every seat fills");
    }

    /** The behavior this replaced, kept as proof the simulation can see the defect. */
    @Test
    void matchingOnlyLoadedWorkersCyclesJobs() {
        assertTrue(simulate(false).releases > 0);
    }

    @Test
    void seatsDecideCareersCarriersAndProprietors() {
        ResourceLocation merchant = ResourceLocation.tryParse("beachparty:sandymerchant");
        ProfessionDef bar = def(ResourceLocation.tryParse("test:bartender"), List.of(
                new JobSiteProvider.Building(List.of("test:bar_l"), List.of(1, 2), List.of(),
                        new JobSiteProvider.Building.Proprietor(Set.of(merchant), 1))));
        ProfessionDef clerk = def(ResourceLocation.tryParse("test:clerk"), List.of(
                new JobSiteProvider.JobBlock(Set.of(ResourceLocation.tryParse("minecraft:lectern")))));

        Set<ResourceLocation> ids = ProfessionCarriers.seatHiredIds(
                Map.of(bar.id(), bar, clerk.id(), clerk), Set.of(), def -> true);

        assertEquals(Set.of(bar.id(), merchant), ids,
                "a reserved proprietor is hired by the seat; a one-block job is not");
        assertTrue(ProfessionCarriers.seatHiredIds(
                Map.of(bar.id(), bar), Set.of(), def -> false).isEmpty(),
                "a career that is switched off hands its jobs back");
    }

    // ---- simulation ----

    private record Churn(int releases, int employed) {}

    /**
     * Twelve residents, three seats, a different half of the village loaded every step. Each
     * loaded resident runs the allocator's two rules: keep the job while a seat is theirs, and
     * take a job when a seat is free.
     */
    private static Churn simulate(boolean keepAbsentWorkers) {
        Random random = new Random(42);
        List<UUID> residents = new ArrayList<>();
        for (int i = 0; i < 12; i++) residents.add(new UUID(1, i));
        Set<UUID> onBooks = new HashSet<>(residents);
        Map<UUID, Long> employedSince = new HashMap<>();
        List<ProfessionSites.Site> sites = seats(3);
        SeatRoll roll = new SeatRoll();
        int releases = 0;

        for (long step = 1; step <= 2000; step++) {
            List<UUID> shuffled = new ArrayList<>(residents);
            Collections.shuffle(shuffled, random);
            List<UUID> loaded = shuffled.subList(0, 6);

            if (!keepAbsentWorkers) roll = new SeatRoll();
            Map<UUID, SeatRoll.Present> working = new HashMap<>();
            for (UUID id : loaded) {
                if (employedSince.containsKey(id)) working.put(id, present(employedSince.get(id)));
            }
            roll.sync(working, new HashSet<>(loaded), onBooks);

            for (UUID id : loaded) {
                if (employedSince.containsKey(id)) {
                    if (roll.seatOf(id, sites, PROVIDERS) < 0) {
                        employedSince.remove(id);
                        roll.dismiss(id);
                        releases++;
                    }
                } else if (roll.seatIfHired(id, COOK, null, sites, PROVIDERS) >= 0) {
                    employedSince.put(id, step);
                    roll.enroll(id, COOK, null, step);
                }
            }
            if (keepAbsentWorkers) {
                assertTrue(employedSince.size() <= sites.size(), "never more workers than seats");
            }
        }
        return new Churn(releases, employedSince.size());
    }

    private static SeatRoll.Present present(long since) {
        return new SeatRoll.Present(COOK, null, since);
    }

    private static List<ProfessionSites.Site> seats(int count) {
        List<ProfessionSites.Site> sites = new ArrayList<>();
        for (int i = 0; i < count; i++) sites.add(new ProfessionSites.Site(null, null, 0));
        return sites;
    }

    private static ProfessionDef def(ResourceLocation id, List<JobSiteProvider> sites) {
        return new ProfessionDef(id, null, null,
                new ProgressionTrack(List.of(0), 0, 0), UnlockModel.EXPERIENTIAL, 1,
                RetrainingPolicy.FREE, List.of(), false, Conditions.ALWAYS, List.of(), sites);
    }
}
