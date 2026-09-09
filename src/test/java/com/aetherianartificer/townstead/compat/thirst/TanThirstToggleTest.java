package com.aetherianartificer.townstead.compat.thirst;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TanThirstToggleTest {
    @Test void readsLiveToggleThroughConfiguredBridge() {
        ThirstCompatBridge bridge = new ConfiguredThirstBridge(ToughAsNailsThirstBridge.INSTANCE);
        toughasnails.api.thirst.ThirstHelper.enabled = false;
        assertFalse(bridge.isThirstEnabled());
        toughasnails.api.thirst.ThirstHelper.enabled = true;
        assertTrue(bridge.isThirstEnabled());
        toughasnails.api.thirst.ThirstHelper.enabled = false;
        assertFalse(bridge.isThirstEnabled());
    }
}
