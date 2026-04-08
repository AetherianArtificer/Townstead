package com.aetherianartificer.townstead.client.gui.dialogue.effect;

/**
 * A text effect applied per-character during dialogue rendering.
 */
@FunctionalInterface
public interface DialogueEffect {
    /**
     * Modify the render state for a single character.
     *
     * @param state      mutable render state to modify
     * @param charIndex  index of this character in the full text
     * @param totalChars total number of characters
     * @param time       animation time (smooth, wrapping float)
     */
    void apply(CharRenderState state, int charIndex, int totalChars, float time);
}
