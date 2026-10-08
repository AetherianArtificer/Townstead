package com.aetherianartificer.townstead.profession.def;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InsightScheduleTest {

    private static final ProgressionTrack FARMER =
            new ProgressionTrack(List.of(0, 120, 320, 700, 1300), 240, 200000);

    private static int earned(int xp) {
        return InsightSchedule.earnedAt(FARMER, level -> 1, xp);
    }

    @Test
    void firstRankPaysNothing() {
        assertEquals(0, earned(0));
        assertEquals(0, earned(29));
    }

    @Test
    void checkpointsSplitEachRankSpanEvenly() {
        assertEquals(1, earned(30));
        assertEquals(2, earned(60));
        assertEquals(3, earned(90));
        assertEquals(4, earned(120), "rank-up pays on top of the checkpoints");
        assertEquals(List.of(30, 60, 90, 120), InsightSchedule.marksThrough(FARMER, 120));
    }

    @Test
    void masterPaysSixteenThenOnePerFinalSpan() {
        assertEquals(16, earned(1300));
        assertEquals(16, earned(1899));
        assertEquals(17, earned(1900), "one more per 600 XP, the Expert to Master span");
        assertEquals(1900, InsightSchedule.nextAfter(FARMER, 1300));
        assertEquals(1300, InsightSchedule.previousAtOrBefore(FARMER, 1450));
    }

    @Test
    void rankPayoutsComeFromTheLevel() {
        assertEquals(5, InsightSchedule.earnedAt(FARMER, level -> level == 2 ? 2 : 1, 120));
    }

    @Test
    void nothingPaysPastTheCeiling() {
        ProgressionTrack capped = new ProgressionTrack(List.of(0, 100), 0, 150);
        assertEquals(-1, InsightSchedule.nextAfter(capped, 100));
        assertEquals(4, InsightSchedule.earnedAt(capped, level -> 1, 150));
    }
}
