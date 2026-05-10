package com.aetherianartificer.townstead.client.gui.pose;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.animation.emote.loader.EmotecraftEmoteList;
import com.aetherianartificer.townstead.client.animation.emote.TownsteadEmoteApi;
import com.aetherianartificer.townstead.emote.EmoteTriggerC2SPayload;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
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
 * screen. Renders Emotecraft's own {@code fastchoose_dark_new.png} atlas at
 * the screen center and overlays our emote icons on top, mirroring the
 * geometry of {@code ModernChooseWheel} 2.4.x exactly. The texture is loaded
 * straight from the Emotecraft mod's resources at runtime — no compile-time
 * binding — and the Pose button is hidden when Emotecraft isn't installed,
 * so the texture is always available when this screen opens.
 */
public class PosePickerScreen extends Screen {
    private static final int SLOTS_PER_PAGE = 8;
    private static final int TEX_SIZE = 512;

    //? if neoforge {
    private static final ResourceLocation WHEEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "emotecraft", "textures/gui/fastchoose_light_new.png");
    //?} else {
    /*private static final ResourceLocation WHEEL_TEXTURE = new ResourceLocation(
            "emotecraft", "textures/gui/fastchoose_light_new.png");
    *///?}

    /** Slot angles in Emotecraft's convention: 0° = bottom, increases clockwise. */
    private static final float[] SLOT_ANGLE_DEG = {0F, 45F, 90F, 135F, 180F, 225F, 270F, 315F};

    private final VillagerLike<?> villager;
    private final LivingEntity villagerEntity;
    private List<EmotecraftEmoteList.Entry> entries = List.of();
    private final Map<ResourceLocation, ResourceLocation> iconTextures = new HashMap<>();
    private final Map<ResourceLocation, DynamicTexture> ownedTextures = new HashMap<>();
    private int page;
    private int hoveredSlot = -1;
    private int widgetSize;
    private int widgetX;
    private int widgetY;
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
        // Match Emotecraft's FastMenuScreen.repositionElements: 80% of the
        // smaller screen dimension, kept square.
        widgetSize = (int) Math.min(width * 0.8, height * 0.8);
        widgetX = width / 2 - widgetSize / 2;
        widgetY = height / 2 - widgetSize / 2;
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

        int totalPages = totalPages();
        int navY = widgetY + widgetSize + 4;

        prevPage = Button.builder(Component.literal("<"), b -> {
                    if (page > 0) { page--; layoutPage(); }
                })
                .bounds(widgetX - 28, height / 2 - 10, 24, 20).build();
        nextPage = Button.builder(Component.literal(">"), b -> {
                    if (page + 1 < totalPages) { page++; layoutPage(); }
                })
                .bounds(widgetX + widgetSize + 4, height / 2 - 10, 24, 20).build();
        prevPage.active = page > 0;
        nextPage.active = page + 1 < totalPages;
        prevPage.visible = totalPages > 1;
        nextPage.visible = totalPages > 1;
        addRenderableWidget(prevPage);
        addRenderableWidget(nextPage);

        addRenderableWidget(Button.builder(
                        Component.translatable("townstead.pose.picker.cancel"),
                        b -> onClose())
                .bounds(width / 2 - 50, navY, 100, 20)
                .build());
    }

    private int totalPages() {
        return Math.max(1, (entries.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
    }

    /**
     * Hit-test in Emotecraft's coordinate convention: angle 0° = bottom, 90° =
     * right, 180° = top, 270° = left, with each slot spanning ±22.5° around
     * its centre.
     */
    private int slotAt(double mouseX, double mouseY) {
        double cx = widgetX + widgetSize / 2.0;
        double cy = widgetY + widgetSize / 2.0;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < widgetSize * 0.17 || dist > widgetSize / 2.0) return -1;

        // atan2(dx, dy) — by swapping the args we put 0° on the +y axis (bottom
        // in screen coords, since y increases downward), matching Emotecraft.
        double rad = Math.atan2(dx, dy);
        double deg = Math.toDegrees(rad);
        if (deg < 0) deg += 360;
        deg = (deg + 22.5) % 360;
        int slot = (int) (deg / 45.0);
        if (slot < 0 || slot >= SLOTS_PER_PAGE) return -1;
        return slot;
    }

    /** First entry shown at the TOP slot (Emotecraft slot 4), then clockwise. */
    private static int slotForEntry(int entryIdx) {
        return (4 - entryIdx + SLOTS_PER_PAGE) % SLOTS_PER_PAGE;
    }

    private static int entryForSlot(int slot) {
        return (4 - slot + SLOTS_PER_PAGE) % SLOTS_PER_PAGE;
    }

    private int globalEntryIndexForSlot(int slot) {
        if (slot < 0) return -1;
        int idx = page * SLOTS_PER_PAGE + entryForSlot(slot);
        return idx < entries.size() ? idx : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || entries.isEmpty()) return false;
        int slot = slotAt(mouseX, mouseY);
        int idx = globalEntryIndexForSlot(slot);
        if (idx < 0) return false;
        onPicked(entries.get(idx));
        return true;
    }

    //? if neoforge {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return townstead$scroll(scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scroll) {
        return townstead$scroll(scroll);
    }
    *///?}

    private boolean townstead$scroll(double delta) {
        int totalPages = totalPages();
        if (totalPages <= 1) return false;
        if (delta > 0 && page > 0) {
            page--;
            layoutPage();
            return true;
        }
        if (delta < 0 && page + 1 < totalPages) {
            page++;
            layoutPage();
            return true;
        }
        return false;
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
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.flush();

        hoveredSlot = slotAt(mouseX, mouseY);
        boolean hoveredHasEmote = globalEntryIndexForSlot(hoveredSlot) >= 0;

        graphics.pose().pushPose();
        graphics.pose().translate(0F, 0F, 400F);

        renderWheel(graphics, hoveredHasEmote);

        // Page indicator + hovered emote name above the wheel.
        int totalPages = totalPages();
        int idx = globalEntryIndexForSlot(hoveredSlot);
        if (idx >= 0) {
            EmotecraftEmoteList.Entry hovered = entries.get(idx);
            graphics.drawCenteredString(font, Component.literal(hovered.displayName()),
                    width / 2, widgetY - 14, 0xFFFFFFFF);
        } else if (totalPages > 1) {
            graphics.drawCenteredString(font,
                    Component.literal((page + 1) + " / " + totalPages)
                            .withStyle(ChatFormatting.GRAY),
                    width / 2, height / 2 - font.lineHeight / 2, 0xCCCCCC);
        }
        graphics.pose().popPose();
    }

    private void renderWheel(GuiGraphics graphics, boolean hoveredHasEmote) {
        // Texture has α=0.6 fills, α=1.0 lines, α=0 gaps baked in. Emotecraft
        // renders at no modulation, so we do the same — enableBlend forces the
        // per-pixel alpha to composite against whatever's behind the wheel.
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        drawWheelTexture(graphics, 0, 0, 0F, 0F, 2);
        if (hoveredHasEmote && hoveredSlot >= 0) {
            renderHoverForSlot(graphics, hoveredSlot);
        }
        graphics.flush();

        int from = page * SLOTS_PER_PAGE;
        int available = Math.min(SLOTS_PER_PAGE, entries.size() - from);
        for (int entryIdx = 0; entryIdx < available; entryIdx++) {
            int slot = slotForEntry(entryIdx);
            EmotecraftEmoteList.Entry entry = entries.get(from + entryIdx);
            drawIcon(graphics, slot, entry);
        }
    }

    /**
     * Mirrors {@code ModernChooseWheel.drawTexture(matrices, t, x, y, u, v, s)}
     * from Emotecraft 2.4.x. Renders an {@code (s*128) × (s*128)} pixel patch
     * of the 512×512 atlas at texture-coords {@code (u, v)} into the widget
     * area at logical coords {@code (x, y)} (where the widget's full size is
     * 256 logical units and the texture patch is scaled to fill {@code s/2} of
     * the widget's width and height).
     */
    private void drawWheelTexture(GuiGraphics graphics, int x, int y, float u, float v, int s) {
        graphics.blit(
                WHEEL_TEXTURE,
                widgetX + x * widgetSize / 256,
                widgetY + y * widgetSize / 256,
                s * widgetSize / 2,
                s * widgetSize / 2,
                u, v,
                s * 128, s * 128,
                TEX_SIZE, TEX_SIZE);
    }

    /**
     * Mirrors {@code ModernChooseWheel.drawTexture_select(matrices, t, x, y,
     * u, v, w, h)} — the hover-overlay variant. The widget's logical grid is
     * 512 units wide here (vs 256 for {@link #drawWheelTexture}) and the patch
     * size is {@code (w*128) × (h*128)}.
     */
    private void drawWheelSelect(GuiGraphics graphics, int x, int y, float u, float v,
                                 int w, int h) {
        graphics.blit(
                WHEEL_TEXTURE,
                widgetX + x * widgetSize / 512,
                widgetY + y * widgetSize / 512,
                w * widgetSize / 2,
                h * widgetSize / 2,
                u, v,
                w * 128, h * 128,
                TEX_SIZE, TEX_SIZE);
    }

    private void renderHoverForSlot(GuiGraphics graphics, int slot) {
        switch (slot) {
            case 0 -> drawWheelSelect(graphics, 0,   256, 0F,   384F, 2, 1);
            case 1 -> drawWheelSelect(graphics, 256, 256, 384F, 384F, 1, 1);
            case 2 -> drawWheelSelect(graphics, 256, 0,   384F, 0F,   1, 2);
            case 3 -> drawWheelSelect(graphics, 256, 0,   384F, 256F, 1, 1);
            case 4 -> drawWheelSelect(graphics, 0,   0,   0F,   256F, 2, 1);
            case 5 -> drawWheelSelect(graphics, 0,   0,   256F, 256F, 1, 1);
            case 6 -> drawWheelSelect(graphics, 0,   0,   256F, 0F,   1, 2);
            case 7 -> drawWheelSelect(graphics, 0,   256, 256F, 384F, 1, 1);
            default -> { /* no overlay */ }
        }
    }

    private void drawIcon(GuiGraphics graphics, int slot, EmotecraftEmoteList.Entry entry) {
        ResourceLocation icon = iconTextures.get(entry.id());
        double angleRad = Math.toRadians(SLOT_ANGLE_DEG[slot]);
        int s = widgetSize / 10;
        int iconX = (int) (widgetX + widgetSize / 2.0 + widgetSize * 0.36 * Math.sin(angleRad)) - s;
        int iconY = (int) (widgetY + widgetSize / 2.0 + widgetSize * 0.36 * Math.cos(angleRad)) - s;
        if (icon != null) {
            graphics.blit(icon, iconX, iconY, 2 * s, 2 * s, 0F, 0F, 256, 256, 256, 256);
        } else {
            String fallback = entry.displayName();
            if (fallback.length() > 3) fallback = fallback.substring(0, 3);
            graphics.drawCenteredString(font, fallback,
                    iconX + s, iconY + s - font.lineHeight / 2, 0xFFCCCCCC);
        }
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
