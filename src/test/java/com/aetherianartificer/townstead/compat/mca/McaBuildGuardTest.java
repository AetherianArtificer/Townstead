package com.aetherianartificer.townstead.compat.mca;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McaBuildGuardTest {
    private static final String RELEASE = "7.7.37+1.21.1";
    private static final String DEVELOPMENT = "1.21.1-SNAPSHOT";

    @Test
    void acceptsSnapshotAndTheTargetRelease() {
        assertTrue(McaBuildGuard.accepts(RELEASE, RELEASE, DEVELOPMENT));
        assertTrue(McaBuildGuard.accepts(DEVELOPMENT, RELEASE, DEVELOPMENT));

        assertFalse(McaBuildGuard.accepts("7.7.36+1.21.1", RELEASE, DEVELOPMENT));
        assertFalse(McaBuildGuard.accepts("7.7.38+1.21.1", RELEASE, DEVELOPMENT));
    }
}
