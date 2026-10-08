package com.aetherianartificer.townstead.client.gui.switchboard;

import com.aetherianartificer.townstead.switchboard.SwitchboardRequestC2SPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.ref.WeakReference;

/**
 * The way into World Settings from Townstead's mod config screen. World settings live on the
 * server, so the button only works inside a world, for an operator or the singleplayer host.
 */
public final class SwitchboardLink {
    private SwitchboardLink() {}

    private static WeakReference<Screen> configScreen = new WeakReference<>(null);

    /** Remembers Townstead's config screen, so the link is added to it and to no other mod's. */
    public static Screen track(Screen screen) {
        configScreen = new WeakReference<>(screen);
        return screen;
    }

    public static Button button(int x, int y, int width) {
        Button button = Button.builder(Component.translatable("townstead.switchboard.open"), b -> request())
                .bounds(x, y, width, 20).build();
        button.active = canOpen();
        if (!button.active) button.setTooltip(Tooltip.create(Component.translatable("townstead.switchboard.open.unavailable")));
        return button;
    }

    //? if neoforge {
    public static void onScreenInit(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen != configScreen.get()) return;
        event.addListener(button(screen.width / 2 - 100, screen.height - 57, 200));
    }
    //?}

    private static boolean canOpen() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && (mc.hasSingleplayerServer() || mc.player.hasPermissions(2));
    }

    private static void request() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new SwitchboardRequestC2SPayload());
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(new SwitchboardRequestC2SPayload());
        *///?}
    }
}
