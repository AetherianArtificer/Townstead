package com.aetherianartificer.townstead.client.catalog;

import com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogVillageThemeTest {
    private VillageSpiritSyncPayload snapshot(String primary, Map<String, Integer> points) {
        return new VillageSpiritSyncPayload(4, points, 100, 3, primary == null ? "MIXED" : "SINGLE", 2,
                Optional.ofNullable(primary), Optional.empty(), Map.of());
    }
    @Test void villageLeaningTintsBackgroundButKeepsRecognitionColors() {
        var base = CatalogDataLoader.Theme.DEFAULT;
        var nautical = CatalogVillageTheme.resolve(base, snapshot("nautical", Map.of("nautical", 60)));
        var pastoral = CatalogVillageTheme.resolve(base, snapshot("pastoral", Map.of("pastoral", 60)));
        assertNotEquals(nautical.graphBackgroundColor(), pastoral.graphBackgroundColor());
        assertNotEquals(base.panelColor(), nautical.panelColor());
        assertEquals(base.builtNodeFillColor(), nautical.builtNodeFillColor());
        assertEquals(base.nodeSelectedBorderColor(), nautical.nodeSelectedBorderColor());
        assertEquals(base.frameColor(), nautical.frameColor());
    }
    @Test void mixedVillageUsesStrongestLeaningAndMissingVillageKeepsBaseTheme() {
        var base = CatalogDataLoader.Theme.DEFAULT;
        assertSame(base, CatalogVillageTheme.resolve(base, null));
        assertEquals(CatalogVillageTheme.resolve(base, snapshot("nautical", Map.of("nautical", 60))),
                CatalogVillageTheme.resolve(base, snapshot(null, Map.of("pastoral", 20, "nautical", 60))));
    }
}
