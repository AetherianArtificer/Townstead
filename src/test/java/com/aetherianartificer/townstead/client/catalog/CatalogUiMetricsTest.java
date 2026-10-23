package com.aetherianartificer.townstead.client.catalog;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatalogUiMetricsTest {
    @Test void highGuiScaleStillFitsTwoTieredGroupsAndInspector() {
        var metrics = CatalogUiMetrics.forScreen(512, 260, 5);
        assertEquals(3, metrics.scale() * 5, 0.00001);
        assertTrue(metrics.width() >= 800);
        assertTrue(metrics.height() >= 420);
        assertTrue(metrics.width() - 32 - 244 - 26 >= 2 * 221 + 12);
    }
    @Test void smallGuiScaleDoesNotEnlargeNativeControls() {
        var metrics = CatalogUiMetrics.forScreen(960, 540, 2);
        assertEquals(1, metrics.scale());
        assertEquals(960, metrics.width());
    }
}
