package com.aetherianartificer.townstead.client.gui.rebirth;

import com.aetherianartificer.townstead.rebirth.Rebirth;
import com.aetherianartificer.townstead.rebirth.RebirthMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Adds "Be reborn" to the death screen. When every death is a rebirth, it takes the place of
 * Respawn, so the player still names the new person.
 */
public final class RebirthDeathButton {
    private RebirthDeathButton() {}

    //? if neoforge {
    public static void onScreenInit(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event) {
    //?} else if forge {
    /*public static void onScreenInit(net.minecraftforge.client.event.ScreenEvent.Init.Post event) {
    *///?}
        if (!(event.getScreen() instanceof DeathScreen screen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Button respawn = null;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents t
                    && t.getKey().equals("deathScreen.respawn")) {
                respawn = button;
            }
        }
        // MCA Descendants continues the bloodline; Townstead only hands the respawn over to it.
        if (com.aetherianartificer.townstead.compat.mcadescendants.DescendantsBridge.canHandOff(mc.player)) {
            int dx = respawn != null ? respawn.getX() : screen.width / 2 - 100;
            event.addListener(Button.builder(Component.translatable("townstead.rebirth.descendant"), b -> {
                        //? if neoforge {
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                new com.aetherianartificer.townstead.rebirth.RebirthRequestC2SPayload("", true));
                        //?} else if forge {
                        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(
                                new com.aetherianartificer.townstead.rebirth.RebirthRequestC2SPayload("", true));
                        *///?}
                        b.active = false;
                        mc.player.respawn();
                    })
                    .bounds(dx, screen.height / 4 + 144, 200, 20).build());
        }
        if (!Rebirth.available(mc.player)) return;
        boolean forced = Rebirth.mode() == RebirthMode.FORCED;
        int x = respawn != null ? respawn.getX() : screen.width / 2 - 100;
        int y = forced && respawn != null ? respawn.getY() : screen.height / 4 + 120;
        if (forced && respawn != null) respawn.visible = false;
        event.addListener(Button.builder(Component.translatable("townstead.rebirth.button"),
                        b -> mc.setScreen(new RebirthScreen(screen)))
                .bounds(x, y, 200, 20).build());
    }
}
