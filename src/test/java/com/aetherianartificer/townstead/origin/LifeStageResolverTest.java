package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LifeStageResolverTest {

    private static LifeStage stage(String id, CanonicalStage canonical, int days, StageEndAction onEnd) {
        return LifeStage.of(id, Component.literal(id), canonical, days, onEnd);
    }

    /** baby[0,2) toddler[2,4) child[4,13) teen[13,18) adult[18,65) senior[65,90) then STAY. */
    private static LifeCycle humanLike() {
        return new LifeCycle(List.of(
                stage("baby", CanonicalStage.BABY, 2, null),
                stage("toddler", CanonicalStage.TODDLER, 2, null),
                stage("child", CanonicalStage.CHILD, 9, null),
                stage("teen", CanonicalStage.TEEN, 5, null),
                stage("adult", CanonicalStage.ADULT, 47, null),
                stage("senior", CanonicalStage.SENIOR, 25, StageEndAction.STAY)
        ));
    }

    private static final int[] HUMAN_DAYS = {2, 2, 9, 5, 47, 25};

    private static LifeStageResolver.Resolved resolveAt(long daysAlive) {
        // birth at day 0, "today" = daysAlive
        return LifeStageResolver.resolve(humanLike(), HUMAN_DAYS, 0L, daysAlive);
    }

    @Test
    void newbornIsFirstStage() {
        LifeStageResolver.Resolved r = resolveAt(0);
        assertEquals(0, r.stageIndex());
        assertEquals(0f, r.deltaInStage(), 1e-6);
    }

    @Test
    void midFirstStage() {
        LifeStageResolver.Resolved r = resolveAt(1);
        assertEquals(0, r.stageIndex());
        assertEquals(0.5f, r.deltaInStage(), 1e-6);
    }

    @Test
    void stageBoundaryStartsNextStageAtZeroDelta() {
        LifeStageResolver.Resolved r = resolveAt(2); // first toddler day
        assertEquals(1, r.stageIndex());
        assertEquals(0f, r.deltaInStage(), 1e-6);
    }

    @Test
    void resolvesEachStageByCumulativeDays() {
        assertEquals(2, resolveAt(4).stageIndex());   // child starts at 4
        assertEquals(3, resolveAt(13).stageIndex());  // teen starts at 13
        assertEquals(4, resolveAt(18).stageIndex());  // adult starts at 18
        assertEquals(5, resolveAt(65).stageIndex());  // senior starts at 65
    }

    @Test
    void deltaWithinAdultStage() {
        LifeStageResolver.Resolved r = resolveAt(64); // adult span [18,65), 46 days in of 47
        assertEquals(4, r.stageIndex());
        assertEquals(46f / 47f, r.deltaInStage(), 1e-6);
    }

    @Test
    void pastEndStaysOnLastStageWithFullDelta() {
        LifeStageResolver.Resolved r = resolveAt(90); // exactly at total
        assertEquals(5, r.stageIndex());
        assertEquals(1f, r.deltaInStage(), 1e-6);
        assertEquals(5, resolveAt(10_000).stageIndex()); // far past — STAY holds
    }

    @Test
    void futureBirthClampsToNewborn() {
        // today before birth → daysAlive clamps to 0
        LifeStageResolver.Resolved r = LifeStageResolver.resolve(humanLike(), HUMAN_DAYS, 100L, 50L);
        assertEquals(0, r.stageIndex());
        assertEquals(0f, r.deltaInStage(), 1e-6);
    }

    @Test
    void loopEndActionWrapsAround() {
        LifeCycle looping = new LifeCycle(List.of(
                stage("a", CanonicalStage.BABY, 3, null),
                stage("b", CanonicalStage.ADULT, 2, StageEndAction.LOOP)
        ));
        int[] days = {3, 2}; // total 5
        // daysAlive 7 → 7 % 5 = 2 → back inside stage a [0,3)
        LifeStageResolver.Resolved r = LifeStageResolver.resolve(looping, days, 0L, 7L);
        assertEquals(0, r.stageIndex());
        assertEquals(2f / 3f, r.deltaInStage(), 1e-6);
    }

    @Test
    void incoherentDataReturnsNull() {
        assertNull(LifeStageResolver.resolve(null, HUMAN_DAYS, 0L, 5L));
        assertNull(LifeStageResolver.resolve(LifeCycle.EMPTY, new int[0], 0L, 5L));
        assertNull(LifeStageResolver.resolve(humanLike(), new int[]{1, 2, 3}, 0L, 5L)); // length mismatch
        assertNull(LifeStageResolver.resolve(humanLike(), null, 0L, 5L));
    }

    @Test
    void cumulativeDaysBefore() {
        assertEquals(0L, LifeStageResolver.cumulativeDaysBefore(HUMAN_DAYS, 0));
        assertEquals(13L, LifeStageResolver.cumulativeDaysBefore(HUMAN_DAYS, 3)); // 2+2+9
        assertEquals(90L, LifeStageResolver.cumulativeDaysBefore(HUMAN_DAYS, 6)); // whole cycle
        assertEquals(90L, LifeStageResolver.cumulativeDaysBefore(HUMAN_DAYS, 99)); // index past end clamps
    }
}
