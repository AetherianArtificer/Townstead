package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.root.ability.ResourceSyncS2CPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceClientStoreTest {
    @AfterEach
    void clear() {
        ResourceClientStore.clear();
    }

    @Test
    void unchangedHeartbeatDoesNotRestartContextualTimer() {
        ResourceSyncS2CPayload.Bar bar = bar(100);
        ResourceClientStore.set(List.of(bar), 1_000L);
        ResourceClientStore.set(List.of(bar), 3_000L);

        assertTrue(ResourceClientStore.visible(4_600L,
                TownsteadConfig.ResourceHudVisibility.CONTEXTUAL, 60, 10, false).isEmpty());
    }

    @Test
    void valueChangeRestartsContextualTimer() {
        ResourceClientStore.set(List.of(bar(100)), 1_000L);
        ResourceClientStore.set(List.of(bar(75)), 4_000L);

        List<ResourceClientStore.Visible> visible = ResourceClientStore.visible(4_100L,
                TownsteadConfig.ResourceHudVisibility.CONTEXTUAL, 60, 10, false);
        assertEquals(1, visible.size());
        assertEquals(1f, visible.get(0).alpha());
    }

    @Test
    void notAtRestStaysVisibleAfterContextWindow() {
        ResourceClientStore.set(List.of(bar(75)), 1_000L);
        assertEquals(1, ResourceClientStore.visible(100_000L,
                TownsteadConfig.ResourceHudVisibility.NOT_AT_REST, 60, 10, false).size());
    }

    private static ResourceSyncS2CPayload.Bar bar(int value) {
        return new ResourceSyncS2CPayload.Bar("townstead:test", value, 0, 100, 100,
                0x3FA0FF,
                "HORIZONTAL", "CONTINUOUS", List.of(), "townstead:plain", "townstead:arcane",
                "TOP_LEFT", "DOTS", 10, 0, 0xFF202020, 0xFF5C5C5C, 0xFF101010, 1,
                "", -1);
    }
}
