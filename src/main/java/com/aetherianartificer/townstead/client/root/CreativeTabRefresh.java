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
        //? if neoforge {
        // Search text matches its own index, which vanilla refreshes only when the screen rebuilds tabs.
        var trees = mc.player.connection.searchTrees();
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (!tab.hasSearchBar()) continue;
            java.util.List<net.minecraft.world.item.ItemStack> items = java.util.List.copyOf(tab.getDisplayItems());
            trees.updateCreativeTooltips(mc.level.registryAccess(), items,
                    net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.getNameSearchKey(tab));
            trees.updateCreativeTags(items, net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.getTagSearchKey(tab));
        }
        //?}
    }
}
