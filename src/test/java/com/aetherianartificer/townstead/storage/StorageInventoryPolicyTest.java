package com.aetherianartificer.townstead.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageInventoryPolicyTest {
    @Test
    void containerViewWinsOverASecondCapabilityView() {
        assertFalse(StorageInventoryPolicy.useItemHandlerView(true),
                "a Container must be counted and mutated through exactly one canonical view");
        assertTrue(StorageInventoryPolicy.useItemHandlerView(false),
                "handler-only modded storage remains automatically supportable");
    }
}
