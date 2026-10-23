package com.aetherianartificer.townstead.client.story;

/**
 * How story marks are drawn: a white bubble, warm parchment, a dark bubble with a glowing mark, or
 * a stone plaque to match the Stoneborn pack. {@code AUTO} is Stoneborn when that pack is active,
 * else Bright.
 */
public enum StoryMarkStyle {
    AUTO,
    BRIGHT,
    PARCHMENT,
    EMBER,
    STONEBORN;

    /** The style actually drawn. */
    public StoryMarkStyle resolved() {
        if (this != AUTO) return this;
        return com.aetherianartificer.townstead.client.compat.StonebornPack.active() ? STONEBORN : BRIGHT;
    }
}
