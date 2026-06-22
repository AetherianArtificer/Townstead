package com.aetherianartificer.townstead.origin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LifeStageBarTest {

    // Three stages with deliberately uneven spans; cumulative = 2, 5, 10.
    private static final int[] DAYS = {2, 3, 5};

    @Test
    void stageIndexByCumulativeDays() {
        assertEquals(0, LifeStageBar.stageIndexForBioAge(DAYS, 0));
        assertEquals(0, LifeStageBar.stageIndexForBioAge(DAYS, 1));
        assertEquals(1, LifeStageBar.stageIndexForBioAge(DAYS, 2)); // stage 1 starts at 2
        assertEquals(1, LifeStageBar.stageIndexForBioAge(DAYS, 4));
        assertEquals(2, LifeStageBar.stageIndexForBioAge(DAYS, 5)); // stage 2 starts at 5
        assertEquals(2, LifeStageBar.stageIndexForBioAge(DAYS, 9));
        assertEquals(2, LifeStageBar.stageIndexForBioAge(DAYS, 100)); // clamps to last
    }

    @Test
    void emptyStageDaysIndexIsMinusOne() {
        assertEquals(-1, LifeStageBar.stageIndexForBioAge(new int[0], 3));
        assertEquals(-1, LifeStageBar.stageIndexForBioAge(null, 3));
    }

    @Test
    void barIsEquidistantByStage() {
        // Each of the 3 stages owns exactly 1/3 of the bar, regardless of day-span.
        // Slider at k/n lands on the first day of stage k.
        assertEquals(0, LifeStageBar.bioForSliderValue(DAYS, 0.0));
        assertEquals(2, LifeStageBar.bioForSliderValue(DAYS, 1.0 / 3.0)); // stage 1 start
        assertEquals(5, LifeStageBar.bioForSliderValue(DAYS, 2.0 / 3.0)); // stage 2 start
        assertEquals(10, LifeStageBar.bioForSliderValue(DAYS, 1.0));      // end (== total)

        // Inverse: the start of each stage maps back to k/n.
        assertEquals(0.0, LifeStageBar.sliderValueForBio(DAYS, 0), 1e-9);
        assertEquals(1.0 / 3.0, LifeStageBar.sliderValueForBio(DAYS, 2), 1e-9);
        assertEquals(2.0 / 3.0, LifeStageBar.sliderValueForBio(DAYS, 5), 1e-9);
    }

    @Test
    void midStagePositionsAreLinearWithinStage() {
        // Halfway through stage 1 (the [2,5) span, midpoint day ~3.5 → rounds to 4).
        assertEquals(4, LifeStageBar.bioForSliderValue(DAYS, 0.5));
    }

    @Test
    void sliderBioRoundTripsForRepresentativeAges() {
        for (int bio : new int[]{0, 1, 2, 3, 4, 5, 7, 9}) {
            double v = LifeStageBar.sliderValueForBio(DAYS, bio);
            assertEquals(bio, LifeStageBar.bioForSliderValue(DAYS, v),
                    "round-trip failed for bioAge=" + bio);
        }
    }

    @Test
    void singleStageSpansWholeBar() {
        int[] one = {7};
        assertEquals(0, LifeStageBar.bioForSliderValue(one, 0.0));
        assertEquals(7, LifeStageBar.bioForSliderValue(one, 1.0));
        assertEquals(0.0, LifeStageBar.sliderValueForBio(one, 0), 1e-9);
        assertEquals(3.0 / 7.0, LifeStageBar.sliderValueForBio(one, 3), 1e-9);
    }

    @Test
    void emptyStageDaysAreSafe() {
        assertEquals(0, LifeStageBar.bioForSliderValue(new int[0], 0.5));
        assertEquals(0, LifeStageBar.bioForSliderValue(null, 0.5));
        assertEquals(0.0, LifeStageBar.sliderValueForBio(new int[0], 5), 1e-9);
        assertEquals(0.0, LifeStageBar.sliderValueForBio(null, 5), 1e-9);
    }

    @Test
    void sliderValueClampsOutOfRangeInputs() {
        assertEquals(0, LifeStageBar.bioForSliderValue(DAYS, -1.0)); // below 0
        assertEquals(10, LifeStageBar.bioForSliderValue(DAYS, 2.0)); // above 1
    }
}
