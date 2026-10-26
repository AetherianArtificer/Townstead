package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.root.RootCatalogEntry;
import com.aetherianartificer.townstead.root.RootDiscoveredS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** Tells every player that someone discovered a Root. */
public final class RootDiscoveryToast {
    private RootDiscoveryToast() {}

    public static void show(RootDiscoveredS2CPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        RootCatalogEntry entry = RootCatalogClient.origin(payload.rootId());
        Component root = entry == null ? Component.literal(payload.rootId())
                : entry.nameKey() != null && I18n.exists(entry.nameKey()) ? Component.translatable(entry.nameKey())
                : Component.literal(entry.name());
        mc.getToasts().addToast(SystemToast.multiline(mc,
                //? if >=1.21 {
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                //?} else {
                /*SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                *///?}
                Component.translatable("townstead.root.discovered.title", root),
                Component.translatable("townstead.root.discovered.body", payload.discoverer(), root)));
    }
}
