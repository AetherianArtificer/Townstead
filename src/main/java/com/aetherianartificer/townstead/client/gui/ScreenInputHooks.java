package com.aetherianartificer.townstead.client.gui;

/**
 * Input a screen mixin handles before the screen does. Screen events and an injection into
 * {@code Screen.removed} call these, so no mixin overrides the screen's own methods: an override
 * merged into another mod's screen replaces that screen's version, or is skipped when another mod
 * overrides the same method.
 */
public interface ScreenInputHooks {
    /** True when the scroll was used, so the screen does not see it. */
    default boolean townstead$screenScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    /** True when the key was used, so the screen does not see it. */
    default boolean townstead$screenKeyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /** After the screen has been removed. */
    default void townstead$screenRemoved() {
    }
}
