package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.TownsteadConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceHudMathTest {
    @Test
    void normalizationHonorsNonZeroMinimumAndClamps() {
        assertEquals(0f, ResourceHudMath.normalized(10, 10, 30));
        assertEquals(0.5f, ResourceHudMath.normalized(20, 10, 30));
        assertEquals(1f, ResourceHudMath.normalized(30, 10, 30));
        assertEquals(0f, ResourceHudMath.normalized(-100, 10, 30));
        assertEquals(1f, ResourceHudMath.normalized(100, 10, 30));
    }

    @Test
    void contextualFadeHasStableHoldAndLinearFade() {
        assertEquals(1f, ResourceHudMath.contextualAlpha(3000, 60, 10));
        assertEquals(0.5f, ResourceHudMath.contextualAlpha(3250, 60, 10));
        assertEquals(0f, ResourceHudMath.contextualAlpha(3500, 60, 10));
    }

    @Test
    void pipsRoundToNearestUnit() {
        assertEquals(0, ResourceHudMath.filledUnits(0.04f, 10));
        assertEquals(5, ResourceHudMath.filledUnits(0.5f, 10));
        assertEquals(10, ResourceHudMath.filledUnits(1f, 10));
    }

    @Test
    void instantExitDoesNotFadeAndSlideUsesEasedDistance() {
        assertEquals(1f, ResourceHudMath.exitAlpha(0.1f,
                TownsteadConfig.ResourceHudExitStyle.INSTANT, 0L, 0));
        assertEquals(0f, ResourceHudMath.exitAlpha(0f,
                TownsteadConfig.ResourceHudExitStyle.INSTANT, 0L, 0));
        assertEquals(0.5f, ResourceHudMath.exitAlpha(0.5f,
                TownsteadConfig.ResourceHudExitStyle.FADE, 0L, 0));
        assertEquals(3, ResourceHudMath.exitSlide(0.5f,
                TownsteadConfig.ResourceHudExitStyle.SLIDE, 10));
        assertEquals(0, ResourceHudMath.exitSlide(0.5f,
                TownsteadConfig.ResourceHudExitStyle.FADE, 10));
    }

    @Test
    void flickerExitKeepsStableEndpoints() {
        assertEquals(1f, ResourceHudMath.exitAlpha(1f,
                TownsteadConfig.ResourceHudExitStyle.FLICKER, 1234L, 42));
        assertEquals(0f, ResourceHudMath.exitAlpha(0f,
                TownsteadConfig.ResourceHudExitStyle.FLICKER, 1234L, 42));
    }
}
