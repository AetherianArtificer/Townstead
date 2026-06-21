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
    /**
     * Set true on connect; cleared once the warm fires successfully. We poll
     * every client tick until BuildingTypes is non-empty rather than capping
     * at a fixed deadline — a player can sit on the title or shader-warmup
     * screen for many minutes before actually entering a world, and the warm
     * needs to fire whenever that happens.
     */
    private static boolean spiritIndexWarmPending = false;

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
            NeoForge.EVENT_BUS.addListener(
                    com.aetherianartificer.townstead.client.species.ClimbRender::onRenderLivingPre);
            NeoForge.EVENT_BUS.addListener(
                    com.aetherianartificer.townstead.client.species.ClimbRender::onRenderLivingPost);
            NeoForge.EVENT_BUS.addListener(
                    com.aetherianartificer.townstead.client.species.ClimbView::onComputeCameraAngles);
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
            MinecraftForge.EVENT_BUS.addListener(
                    com.aetherianartificer.townstead.client.species.ClimbRender::onRenderLivingPre);
            MinecraftForge.EVENT_BUS.addListener(
                    com.aetherianartificer.townstead.client.species.ClimbRender::onRenderLivingPost);
            MinecraftForge.EVENT_BUS.addListener(
                    com.aetherianartificer.townstead.client.species.ClimbView::onComputeCameraAngles);
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
        spiritIndexWarmPending = true;
    }

    /**
     * Wait for MCA's data-pack-synced BuildingTypes to be non-empty, then
     * dispatch an async prewarm of the spirit-companion JSON index. Drops the
     * 12 ms classpath-scan stall the player would otherwise eat on first
     * blueprint open.
     */
    private static void tryWarmSpiritIndex() {
        if (!spiritIndexWarmPending) return;
        try {
            java.util.Set<String> types =
                    net.conczin.mca.resources.BuildingTypes.getInstance().getBuildingTypes().keySet();
            if (types.isEmpty()) return;
            com.aetherianartificer.townstead.spirit.BuildingSpiritIndex.prewarmAsync(types);
            com.aetherianartificer.townstead.client.catalog.RequirementNameResolver.prewarmAllFromBuildingTypes();
            com.aetherianartificer.townstead.client.catalog.ModDisplayNameResolver.prewarmAllFromBuildingTypes();
            spiritIndexWarmPending = false;
        } catch (Throwable ignored) {
            // Registry not ready yet — keep polling on subsequent ticks.
        }
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
        clearClientStore("com.aetherianartificer.townstead.calendar.CalendarStampClientStore");
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
        FishermanLineRenderer.onClientTick();
        tryWarmSpiritIndex();
        com.aetherianartificer.townstead.client.species.ClimbState.tick();
        com.aetherianartificer.townstead.client.animation.emote.loader.EmotecraftEventBridge.ensureRegistered();
    }
    //?} else if forge {
    /*private static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        TownsteadKeybinds.onClientTick();
        FishermanLineRenderer.onClientTick();
        tryWarmSpiritIndex();
        com.aetherianartificer.townstead.client.species.ClimbState.tick();
        com.aetherianartificer.townstead.client.animation.emote.loader.EmotecraftEventBridge.ensureRegistered();
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
