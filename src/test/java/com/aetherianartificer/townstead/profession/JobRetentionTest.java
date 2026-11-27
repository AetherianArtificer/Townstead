package com.aetherianartificer.townstead.profession;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobRetentionTest {

    private static final long DAY = JobRetention.NO_SITE_GRACE_TICKS;

    @Test
    void jobIsKeptForAsLongAsTheVillageHasABlockOfItsType() {
        assertFalse(JobRetention.mayLose(true, 0, 1));
        assertFalse(JobRetention.mayLose(true, 0, DAY * 1000),
                "a failed walk or a lost tie never costs the job");
    }

    @Test
    void brokenBlockPutBackWithinADayKeepsTheWorker() {
        assertFalse(JobRetention.mayLose(false, 500, 500));
        assertFalse(JobRetention.mayLose(false, 500, 500 + DAY - 1));
    }

    @Test
    void jobEndsADayAfterTheLastBlockIsGone() {
        assertTrue(JobRetention.mayLose(false, 500, 500 + DAY));
    }

    @Test
    void professionsPageShowsWhereAWorkerStands() {
        assertEquals(JobRetention.WORKING,
                JobRetention.siteStatus(true, true, true, 0, 50).state());

        JobRetention.Status looking = JobRetention.siteStatus(false, true, true, 0, 50);
        assertEquals(JobRetention.LOOKING, looking.state());
        assertEquals(-1, looking.endsInTicks(), "a block still exists, so nothing is counting down");

        assertEquals(JobRetention.LOOKING,
                JobRetention.siteStatus(false, false, false, 0, 50).state(),
                "a worker MCA never unemploys has no countdown either");

        JobRetention.Status ending = JobRetention.siteStatus(false, true, false, 1000, 1000 + 6800);
        assertEquals(JobRetention.ENDING, ending.state());
        assertEquals(DAY - 6800, ending.endsInTicks());
        assertEquals(0, JobRetention.siteStatus(false, true, false, 0, DAY * 3).endsInTicks());

        JobRetention.Status seat = JobRetention.seatStatus(true, 1000, 1600);
        assertEquals(JobRetention.ENDING, seat.state());
        assertTrue(seat.seat());
        assertEquals(JobRetention.SEAT_GRACE_TICKS - 600, seat.endsInTicks());
        assertEquals(JobRetention.WORKING, JobRetention.seatStatus(false, 0, 1600).state());
    }

    @Test
    void countdownReadsAsMinutesAndSeconds() {
        var job = new com.aetherianartificer.townstead.village.VillageResidentClientStore.JobStatus(
                JobRetention.ENDING, 17200, false, "minecraft:composter");
        assertEquals("14:20", com.aetherianartificer.townstead.village.VillageResidentClientStore
                .JobStatus.clock(job.remainingTicks(0)));
        assertEquals("14:05", com.aetherianartificer.townstead.village.VillageResidentClientStore
                .JobStatus.clock(job.remainingTicks(300)));
        assertEquals("0:00", com.aetherianartificer.townstead.village.VillageResidentClientStore
                .JobStatus.clock(job.remainingTicks(99999)));
        assertEquals(-1, com.aetherianartificer.townstead.village.VillageResidentClientStore
                .JobStatus.WORKING.remainingTicks(40));
    }

    @Test
    void seatedWorkerWaitsForAMissingSeatBeforeRelease() {
        long grace = JobRetention.SEAT_GRACE_TICKS;
        assertFalse(JobRetention.seatGraceOver(1000, 1000));
        assertFalse(JobRetention.seatGraceOver(1000, 1000 + grace - 1),
                "a building rescanned for a moment costs nobody a job");
        assertTrue(JobRetention.seatGraceOver(1000, 1000 + grace));
    }
}
