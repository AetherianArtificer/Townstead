package com.aetherianartificer.townstead.client.attachment;

/**
 * How much attachment physics the client runs. {@code LOW} simulates nearby bearers only,
 * without wind; Reduce Motion caps the setting at {@code LOW}.
 */
public enum PhysicsQuality {
    OFF,
    LOW,
    FULL
}
