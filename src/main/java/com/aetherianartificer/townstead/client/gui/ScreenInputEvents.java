package com.aetherianartificer.townstead.client.gui;

/** Hands screen input to {@link ScreenInputHooks} before the screen sees it. */
public final class ScreenInputEvents {
    private ScreenInputEvents() {}

    //? if neoforge {
    public static void onScroll(net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScreen() instanceof ScreenInputHooks hooks
                && hooks.townstead$screenScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    public static void onKey(net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof ScreenInputHooks hooks
                && hooks.townstead$screenKeyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }
    //?} else {
    /*public static void onScroll(net.minecraftforge.client.event.ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScreen() instanceof ScreenInputHooks hooks
                && hooks.townstead$screenScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }

    public static void onKey(net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof ScreenInputHooks hooks
                && hooks.townstead$screenKeyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }
    *///?}
}
