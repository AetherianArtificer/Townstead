package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

/**
 * Rebuilds Townstead's creative tab (and the search tab that mirrors it) once the root catalog has
 * arrived. Vanilla builds tabs at login, before the catalog sync lands, so content gated on loaded
 * roots would otherwise be decided against an empty catalog.
 */
public final class CreativeTabRefresh {

    private CreativeTabRefresh() {}

    public static void refreshTownsteadTab() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        CreativeModeTab.ItemDisplayParameters params = new CreativeModeTab.ItemDisplayParameters(
                mc.player.connection.enabledFeatures(), mc.player.canUseGameMasterBlocks(), mc.level.registryAccess());
        Townstead.TOWNSTEAD_TAB.get().buildContents(params);
        CreativeModeTabs.searchTab().buildContents(params);
    }
}
