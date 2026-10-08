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

    @Test
    void recordsValueAndGameplayTransitionsWithoutFiringOnFirstSnapshot() {
        ResourceClientStore.set(List.of(bar(40, false, 0)), 1_000L);
        ResourceClientStore.Visible initial = ResourceClientStore.visible(1_000L,
                TownsteadConfig.ResourceHudVisibility.ALWAYS, 60, 10, false).get(0);
        assertEquals(Long.MIN_VALUE, initial.reactions().valueChangedAtMillis());

        ResourceClientStore.set(List.of(bar(100, true, 1)), 2_000L);
        ResourceClientStore.ReactionState state = ResourceClientStore.visible(2_000L,
                TownsteadConfig.ResourceHudVisibility.ALWAYS, 60, 10, false).get(0).reactions();
        assertEquals(40, state.previousValue());
        assertEquals(2_000L, state.valueChangedAtMillis());
        assertEquals(2_000L, state.fullChargeAtMillis());
        assertEquals(2_000L, state.regenerationAtMillis());
        assertEquals(2_000L, state.abilityReadyAtMillis());

        ResourceClientStore.set(List.of(bar(0, true, 1)), 3_000L);
        state = ResourceClientStore.visible(3_000L,
                TownsteadConfig.ResourceHudVisibility.ALWAYS, 60, 10, false).get(0).reactions();
        assertEquals(100, state.previousValue());
        assertEquals(3_000L, state.emptyAtMillis());
    }

    private static ResourceSyncS2CPayload.Bar bar(int value) {
        return bar(value, false, 0);
    }

    private static ResourceSyncS2CPayload.Bar bar(int value, boolean ready, int regenerationSequence) {
        return new ResourceSyncS2CPayload.Bar("townstead:test", value, 0, 100, 100,
                0x3FA0FF,
                "HORIZONTAL", "CONTINUOUS", List.of(), List.of(), ready, regenerationSequence,
                "townstead:plain", "townstead:arcane",
                "TOP_LEFT", "DOTS", 10, 0, 0xFF202020, 0xFF5C5C5C, 0xFF101010, 1,
                "", -1, "", "", "");
    }
}
