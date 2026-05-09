package com.aetherianartificer.townstead.client.gui.pose;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.animation.emote.loader.EmotecraftEmoteList;
import com.aetherianartificer.townstead.client.animation.emote.TownsteadEmoteApi;
import com.aetherianartificer.townstead.emote.EmoteTriggerC2SPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

/**
 * Pose-picker overlay opened from the {@code Pose} button on MCA's interact
 * screen. Lists every Emotecraft animation the local player has loaded with
 * Emotecraft's per-emote icon, sends the chosen one to the server (so trackers
 * see the villager pose), and runs it locally so the host's view matches
 * without waiting for a round trip.
 */
public class PosePickerScreen extends Screen {
    private static final int BUTTON_W = 160;
    private static final int BUTTON_H = 28;
    private static final int COLUMNS = 2;
    private static final int ROWS = 5;
    private static final int PAGE_SIZE = COLUMNS * ROWS;
    private static final int GAP = 4;

    private final VillagerLike<?> villager;
    private final LivingEntity villagerEntity;
    private List<EmotecraftEmoteList.Entry> entries = List.of();
    private final Map<ResourceLocation, ResourceLocation> iconTextures = new HashMap<>();
    private final Map<ResourceLocation, DynamicTexture> ownedTextures = new HashMap<>();
    private int page;
    private Button prevPage;
    private Button nextPage;

    public PosePickerScreen(VillagerLike<?> villager) {
        super(Component.translatable("townstead.pose.picker.title"));
        this.villager = villager;
        this.villagerEntity = villager.asEntity();
    }

    @Override
    protected void init() {
        super.init();
        if (entries.isEmpty()) {
            entries = EmotecraftEmoteList.snapshotAndRegister();
            registerIconTextures();
        }
        layoutPage();
    }

    private void registerIconTextures() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        for (EmotecraftEmoteList.Entry entry : entries) {
            byte[] bytes = entry.iconBytes();
            if (bytes == null || bytes.length == 0) continue;
            try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
                NativeImage image = NativeImage.read(in);
                DynamicTexture texture = new DynamicTexture(image);
                String safe = entry.id().getPath().replaceAll("[^a-z0-9_./-]", "_");
                //? if neoforge {
                ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath(
                        Townstead.MOD_ID, "dynamic/pose_icon/" + safe);
                //?} else {
                /*ResourceLocation textureId = new ResourceLocation(
                        Townstead.MOD_ID, "dynamic/pose_icon/" + safe);
                *///?}
                client.getTextureManager().register(textureId, texture);
                iconTextures.put(entry.id(), textureId);
                ownedTextures.put(textureId, texture);
            } catch (Throwable ignored) {
                // Bad PNG bytes — silently fall back to no icon for this entry.
            }
        }
    }

    private void layoutPage() {
        clearWidgets();
        if (entries.isEmpty()) {
            addRenderableWidget(Button.builder(
                            Component.translatable("townstead.pose.picker.cancel"),
                            b -> onClose())
                    .bounds(width / 2 - 50, height - 30, 100, 20)
                    .build());
            return;
        }

        int totalWidth = COLUMNS * BUTTON_W + (COLUMNS - 1) * GAP;
        int totalHeight = ROWS * BUTTON_H + (ROWS - 1) * GAP;
        int startX = (width - totalWidth) / 2;
        int startY = (height - totalHeight) / 2;

        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            int idx = i - from;
            int col = idx % COLUMNS;
            int row = idx / COLUMNS;
            int bx = startX + col * (BUTTON_W + GAP);
            int by = startY + row * (BUTTON_H + GAP);
            EmotecraftEmoteList.Entry entry = entries.get(i);
            ResourceLocation icon = iconTextures.get(entry.id());
            addRenderableWidget(new PoseEntryButton(
                    bx, by, BUTTON_W, BUTTON_H,
                    Component.literal(entry.displayName()),
                    b -> onPicked(entry),
                    icon));
        }

        int navY = startY + totalHeight + GAP * 3;
        int totalPages = (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        prevPage = Button.builder(Component.literal("<"), b -> {
                    if (page > 0) { page--; layoutPage(); }
                })
                .bounds(startX, navY, 24, 20).build();
        nextPage = Button.builder(Component.literal(">"), b -> {
                    if (page + 1 < totalPages) { page++; layoutPage(); }
                })
                .bounds(startX + totalWidth - 24, navY, 24, 20).build();
        prevPage.active = page > 0;
        nextPage.active = page + 1 < totalPages;
        addRenderableWidget(prevPage);
        addRenderableWidget(nextPage);

        addRenderableWidget(Button.builder(
                        Component.translatable("townstead.pose.picker.cancel"),
                        b -> onClose())
                .bounds(width / 2 - 50, navY + 24, 100, 20)
                .build());
    }

    private void onPicked(EmotecraftEmoteList.Entry entry) {
        TownsteadEmoteApi.trigger(villagerEntity, entry.id());
        sendToServer(entry);
        onClose();
    }

    private void sendToServer(EmotecraftEmoteList.Entry entry) {
        EmoteTriggerC2SPayload payload = new EmoteTriggerC2SPayload(
                villagerEntity.getId(), entry.id().toString(), (byte) -1, 1.0F);
        //? if neoforge {
        PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if neoforge {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        //?} else {
        /*renderBackground(graphics);
        *///?}

        graphics.drawCenteredString(font, getTitle(), width / 2, 24, 0xFFFFFF);

        if (entries.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("townstead.pose.picker.empty")
                            .withStyle(ChatFormatting.GRAY),
                    width / 2, height / 2, 0xCCCCCC);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void removed() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            for (Map.Entry<ResourceLocation, DynamicTexture> e : ownedTextures.entrySet()) {
                try {
                    client.getTextureManager().release(e.getKey());
                    e.getValue().close();
                } catch (Throwable ignored) {
                }
            }
        }
        ownedTextures.clear();
        iconTextures.clear();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void open(VillagerLike<?> villager) {
        if (villager == null) return;
        Townstead.LOGGER.debug("Opening PosePickerScreen for {}",
                villager.asEntity().getName().getString());
        Minecraft.getInstance().setScreen(new PosePickerScreen(villager));
    }
}
