package com.aetherianartificer.townstead.climate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CropClimateTest {
    @Test
    void cropsStallOutsideTheirRange() {
        assertEquals(CropClimate.Verdict.GROWS, CropClimate.judge(14f, 12f, Float.POSITIVE_INFINITY));
        assertEquals(CropClimate.Verdict.TOO_COLD, CropClimate.judge(8f, 12f, Float.POSITIVE_INFINITY));
        assertEquals(CropClimate.Verdict.TOO_HOT, CropClimate.judge(30f, 0f, 26f));
        assertEquals(CropClimate.Verdict.GROWS, CropClimate.judge(0f, 0f, 26f));
    }

    @Test
    void seasonTagsPickAClass() {
        assertEquals("hardy", CropClimate.classFromSeasons(false, false, true, true, false));
        assertEquals("hardy", CropClimate.classFromSeasons(false, false, false, false, true));
        assertEquals("warm_season", CropClimate.classFromSeasons(false, true, false, false, false));
        assertEquals("cool_season", CropClimate.classFromSeasons(true, false, true, false, false));
        assertNull(CropClimate.classFromSeasons(true, true, true, false, false));
        assertNull(CropClimate.classFromSeasons(false, false, false, false, false));
    }
}
