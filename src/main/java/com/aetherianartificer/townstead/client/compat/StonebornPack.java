package com.aetherianartificer.townstead.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;

import java.util.Locale;

/**
 * Whether the Stoneborn GUI resource pack is active, so Townstead's own screens can wear their
 * Stoneborn look (our art in its palette, never its files). Checked again on every resource reload.
 */
public final class StonebornPack {
    private static volatile boolean active;

    private StonebornPack() {}

    public static boolean active() {
        return active;
    }

    public static void refresh() {
        boolean found = false;
        try {
            for (Pack pack : Minecraft.getInstance().getResourcePackRepository().getSelectedPacks()) {
                String id = pack.getId().toLowerCase(Locale.ROOT);
                String title = pack.getTitle().getString().toLowerCase(Locale.ROOT);
                // Its own compatibility packs carry the name too, and only load on top of it.
                if (id.contains("stoneborn") || title.contains("stoneborn")) {
                    found = true;
                    break;
                }
            }
        } catch (Throwable ignored) {
            // No pack list yet; stay with the default look.
        }
        active = found;
    }
}
