package com.aetherianartificer.townstead;

import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
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
import com.aetherianartificer.townstead.hunger.HungerClientStore;
import com.aetherianartificer.townstead.farming.FarmingPolicyClientStore;
import com.aetherianartificer.townstead.hunger.ButcherPolicyClientStore;
import com.aetherianartificer.townstead.fatigue.FatigueClientStore;
import com.aetherianartificer.townstead.thirst.ThirstClientStore;
import com.aetherianartificer.townstead.shift.ShiftClientStore;
import com.aetherianartificer.townstead.profession.ProfessionClientStore;

public final class TownsteadClient {
    private static boolean hooksRegistered;

    private TownsteadClient() {}

    public static void registerConfigScreen(ModContainer modContainer) {
        //? if neoforge {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (container, parent) -> new ConfigurationScreen(container, parent));
        if (!hooksRegistered) {
            NeoForge.EVENT_BUS.addListener(TownsteadClient::onPlaySound);
            NeoForge.EVENT_BUS.addListener(TownsteadClient::onClientDisconnect);
            hooksRegistered = true;
        }
        //?} else if forge {
        /*modContainer.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) ->
                        new ConfigInfoScreen(parent)));
        if (!hooksRegistered) {
            MinecraftForge.EVENT_BUS.addListener(TownsteadClient::onPlaySound);
            MinecraftForge.EVENT_BUS.addListener(TownsteadClient::onClientDisconnect);
            hooksRegistered = true;
        }
        *///?}
    }

    //? if neoforge {
    private static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
    //?} else if forge {
    /*private static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
    *///?}
        HungerClientStore.clear();
        ThirstClientStore.clear();
        FatigueClientStore.clear();
        FarmingPolicyClientStore.clear();
        ButcherPolicyClientStore.clear();
        ShiftClientStore.clear();
        ProfessionClientStore.clear();
    }

    private static void onPlaySound(PlaySoundEvent event) {
        if (!TownsteadConfig.isMoodVocalizationMuteEnabled()) return;
        if (event.getSound() == null) return;
        ResourceLocation location = event.getSound().getLocation();
        if (!"mca".equals(location.getNamespace())) return;

        String path = location.getPath();
        boolean villagerMoodPath = path.startsWith("villager.")
                && (path.contains(".laugh") || path.contains(".cry") || path.contains(".celebrate"));
        boolean directClipPath = path.contains("/laugh/") || path.contains("/cry/");
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
