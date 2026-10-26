package com.aetherianartificer.townstead.client.gui.inventory;

import com.aetherianartificer.townstead.Townstead;

/** Binds the villager inventory menu to its screen on the client. */
public final class VillagerInventoryScreens {

    private VillagerInventoryScreens() {}

    public static void register(Object modBus) {
        //? if neoforge {
        ((net.neoforged.bus.api.IEventBus) modBus).addListener(
                (net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) ->
                        event.register(Townstead.VILLAGER_INVENTORY_MENU.get(), VillagerInventoryScreen::new));
        //?} else {
        /*((net.minecraftforge.eventbus.api.IEventBus) modBus).addListener(
                (net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(() ->
                        net.minecraft.client.gui.screens.MenuScreens.register(
                                Townstead.VILLAGER_INVENTORY_MENU.get(), VillagerInventoryScreen::new)));
        *///?}
    }
}
