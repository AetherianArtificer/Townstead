package com.aetherianartificer.townstead.performance;

import com.aetherianartificer.townstead.client.animation.FatigueAnimationSourceAdapter;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FatigueAmbientTest {
    @Test void postureStartsAtTiredAndPersistsThroughTheEarlierStages() {
        assertFalse(FatigueAnimationSourceAdapter.eligible(FatigueData.TIRED_THRESHOLD - 1, false, false, false));
        for (int fatigue : new int[]{FatigueData.TIRED_THRESHOLD, FatigueData.DROWSY_THRESHOLD, FatigueData.EXHAUSTED_THRESHOLD})
            assertTrue(FatigueAnimationSourceAdapter.eligible(fatigue, false, false, false));
        assertFalse(FatigueAnimationSourceAdapter.eligible(20, false, false, true));
        assertFalse(FatigueAnimationSourceAdapter.eligible(12, true, false, false));
        assertFalse(FatigueAnimationSourceAdapter.eligible(12, false, true, false));
    }
    @Test void YawnsHaveFractionalTimingAndStaggerAcrossVillagers() {
        assertEquals(40.5F, FatigueAnimationSourceAdapter.phase(900, 40, .5F));
        assertEquals(FatigueAnimationSourceAdapter.phase(10, -123, .25F),
                FatigueAnimationSourceAdapter.phase(910, -123, .25F));
        assertNotEquals(FatigueAnimationSourceAdapter.phase(10, 0, 0),
                FatigueAnimationSourceAdapter.phase(10, 100, 0));
    }
}
