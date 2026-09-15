package com.aetherianartificer.townstead.client.animation.nativeclip;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SocialPlaybackTimingTest {
    @Test void variationIsBoundedAndDoesNotDelayShortGesturesOutOfTheirLifetime() {
        for (int seed : new int[]{0, 1, 53891, -72816, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertTrue(SocialPlaybackTiming.speed(seed) >= 0.92F);
            assertTrue(SocialPlaybackTiming.speed(seed) <= 1.081F);
            for (int duration : new int[]{1, 5, 20, 60, 360}) {
                int delay = SocialPlaybackTiming.delay(seed, duration);
                assertTrue(delay >= 0 && delay <= 24 && delay < duration);
            }
        }
    }

    @Test void unrelatedAnimationChannelsKeepTheirAuthoredTiming() {
        assertTrue(SocialPlaybackTiming.varies("social"));
        assertTrue(SocialPlaybackTiming.varies("hospitality"));
        assertFalse(SocialPlaybackTiming.varies("combat"));
        assertFalse(SocialPlaybackTiming.varies("work"));
    }
}
