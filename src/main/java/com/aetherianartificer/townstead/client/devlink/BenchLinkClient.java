package com.aetherianartificer.townstead.client.devlink;

import com.aetherianartificer.townstead.devlink.BenchAnchors;
import com.aetherianartificer.townstead.devlink.BenchLinkActionC2SPayload;
import com.aetherianartificer.townstead.devlink.BenchLinkItem;
import com.aetherianartificer.townstead.devlink.BenchLinkStatusS2CPayload;
import net.minecraft.client.Minecraft;

/** Client entry points for Bench Link: setup, the status handler, sending screen actions, gizmos. */
public final class BenchLinkClient {

    private BenchLinkClient() {}

    /** Registers the anchor capture, so an integrated server can answer the anchors endpoint. */
    public static void init() {
        BenchAnchors.install(BenchAnchorCapture.INSTANCE);
    }

    public static void onStatus(BenchLinkStatusS2CPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BenchLinkScreen screen) {
            screen.update(payload);
        } else if (payload.open() && mc.screen == null) {
            mc.setScreen(new BenchLinkScreen(payload));
        }
    }

    public static void send(int action) {
        BenchLinkActionC2SPayload payload = new BenchLinkActionC2SPayload(action);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    /** True while the local player holds the Bench Link, so render layers draw anchor gizmos. */
    public static boolean gizmos() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && BenchLinkItem.holding(mc.player);
    }
}
