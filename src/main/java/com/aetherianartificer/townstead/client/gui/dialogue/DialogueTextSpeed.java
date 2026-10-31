package com.aetherianartificer.townstead.client.gui.dialogue;

/** How fast dialogue text types out. {@code INSTANT} shows each line whole. */
public enum DialogueTextSpeed {
    SLOW(0.5f),
    NORMAL(1.0f),
    FAST(2.0f),
    INSTANT(0f);

    private final float speed;

    DialogueTextSpeed(float speed) {
        this.speed = speed;
    }

    public float speed() {
        return speed;
    }
}
