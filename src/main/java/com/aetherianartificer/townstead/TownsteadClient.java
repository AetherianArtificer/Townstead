package com.aetherianartificer.townstead;

import com.aetherianartificer.townstead.client.TownsteadKeybinds;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.travelerstitles.ClientCapsPayload;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.hunger.FishermanLineRenderer;
import com.aetherianartificer.townstead.hunger.FishingRodCastPredicates;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import com.aetherianartificer.townstead.fatigue.EnergyTooltipComponent;
import com.mojang.datafixers.util.Either;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
//?} else if forge {
/*import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
*///?}
import java.lang.reflect.Method;

public final class TownsteadClient {
    private static boolean hooksRegistered;

    private TownsteadClient() {}

    public static void registerConfigScreen(ModContainer modContainer) {
        //? if neoforge {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (container, parent) -> new ConfigurationScreen(container, parent));
        if (!hooksRegistered) {
            NeoForge.EVENT_BUS.addListener(TownsteadClient::onPlaySound);
            NeoForge.EVENT_BUS.addListener(TownsteadClient::onClientConnect);
            NeoForge.EVENT_BUS.addListener(TownsteadClient::onClientDisconnect);
            NeoForge.EVENT_BUS.addListener(TownsteadClient::onGatherTooltipComponents);
            NeoForge.EVENT_BUS.addListener(TownsteadClient::onClientTick);
            NeoForge.EVENT_BUS.addListener(FishermanLineRenderer::onRenderLevel);
            hooksRegistered = true;
        }
        //?} else if forge {
        /*if (!net.minecraftforge.fml.ModList.get().isLoaded("configured")) {
            modContainer.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) ->
                            new ConfigInfoScreen(parent)));
        }
        if (!hooksRegistered) {
            MinecraftForge.EVENT_BUS.addListener(TownsteadClient::onPlaySound);
            MinecraftForge.EVENT_BUS.addListener(TownsteadClient::onClientConnect);
            MinecraftForge.EVENT_BUS.addListener(TownsteadClient::onClientDisconnect);
            MinecraftForge.EVENT_BUS.addListener(TownsteadClient::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(FishermanLineRenderer::onRenderLevel);
            hooksRegistered = true;
        }
        *///?}
    }

    //? if neoforge {
    private static void onClientConnect(ClientPlayerNetworkEvent.LoggingIn event) {
    //?} else if forge {
    /*private static void onClientConnect(ClientPlayerNetworkEvent.LoggingIn event) {
    *///?}
        boolean hasTT = ModCompat.isLoaded("travelerstitles");
        //? if neoforge {
        PacketDistributor.sendToServer(new ClientCapsPayload(hasTT));
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(new ClientCapsPayload(hasTT));
        *///?}
        // Wrap fishing-rod cast predicates so villagers show the "cast"
        // model variant while their hook is in the water. Safe to call
        // every reconnect — the wrapper is idempotent.
        FishingRodCastPredicates.registerOnce();
    }

    //? if neoforge {
    private static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
    //?} else if forge {
    /*private static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
    *///?}
        clearClientStore("com.aetherianartificer.townstead.hunger.HungerClientStore");
        clearClientStore("com.aetherianartificer.townstead.hunger.FishermanHookLinkStore");
        clearClientStore("com.aetherianartificer.townstead.thirst.ThirstClientStore");
        clearClientStore("com.aetherianartificer.townstead.fatigue.FatigueClientStore");
        clearClientStore("com.aetherianartificer.townstead.farming.FarmingPolicyClientStore");
        clearClientStore("com.aetherianartificer.townstead.shift.ShiftClientStore");
        clearClientStore("com.aetherianartificer.townstead.profession.ProfessionClientStore");
        clearClientStore("com.aetherianartificer.townstead.village.VillageResidentClientStore");
    }

    private static void clearClientStore(String className) {
        try {
            Class<?> storeClass = Class.forName(className);
            Method clearMethod = storeClass.getDeclaredMethod("clear");
            clearMethod.invoke(null);
        } catch (Throwable ignored) {
            // Disconnect cleanup must never crash the client.
        }
    }

    //? if neoforge {
    private static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        TownsteadKeybinds.onClientTick();
    }
    //?} else if forge {
    /*private static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        TownsteadKeybinds.onClientTick();
    }
    *///?}

    //? if neoforge {

    private static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        if (FatigueData.isEnergyRestoring(event.getItemStack())) {
            event.getTooltipElements().add(Either.right(
                    new EnergyTooltipComponent(FatigueData.ENERGY_RESTORE_AMOUNT)));
        }
    }
    //?}

    private static void onPlaySound(PlaySoundEvent event) {
        if (!TownsteadConfig.isMoodVocalizationMuteEnabled()) return;
        if (event.getSound() == null) return;
        ResourceLocation location = event.getSound().getLocation();
        if (!"mca".equals(location.getNamespace())) return;

        String path = location.getPath();
        boolean villagerMoodPath = path.startsWith("villager.")
                && (path.contains(".laugh") || path.contains(".cry") || path.contains(".celebrate"));
        boolean directClipPath = path.contains("/laugh/") || path.contains("/cry/") || path.contains("/celebrate/");
        if (villagerMoodPath || directClipPath) {
            event.setSound(null);
        }
    }

    //? if forge {
    /*private static class ConfigInfoScreen extends Screen {
        private final Screen parent;

        ConfigInfoScreen(Screen parent) {
            super(Component.literal("Townstead Configuration"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> minecraft.setScreen(parent))
                    .bounds(width / 2 - 100, height - 28, 200, 20)
                    .build());
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
            int y = 50;
            graphics.drawCenteredString(font, "Server config: <world>/serverconfig/townstead-server.toml", width / 2, y, 0xAAAAAA);
            graphics.drawCenteredString(font, "Client config: config/townstead-client.toml", width / 2, y + 14, 0xAAAAAA);
            graphics.drawCenteredString(font, "Edit these files with a text editor.", width / 2, y + 36, 0xCCCCCC);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }
    *///?}
}
