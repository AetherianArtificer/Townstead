package com.aetherianartificer.townstead.client.gui.ability;

import com.aetherianartificer.townstead.client.gui.common.FrameRenderer;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.client.gui.common.ParchmentButton;
import com.aetherianartificer.townstead.client.root.ClientAbilityLoadout;
import com.aetherianartificer.townstead.root.ability.AbilityLoadoutC2SPayload;
import com.aetherianartificer.townstead.root.ability.AbilityLoadoutS2CPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What goes in one slot: everything you own, grouped by where it came from.
 *
 * <p>A persistent screen rather than part of the wheel's hold gesture. Choosing from hundreds is
 * reading and scrolling, and neither survives being done with a key held down.</p>
 *
 * <p>Root abilities and career skills sit in one list under their own headings, because preparing is
 * a decision ACROSS everything you are, and the career board can only ever show one career. That is
 * the whole reason this is its own surface and not a button on the record page.</p>
 */
public final class AbilitySlotPickerScreen extends Screen {

    private static final int PANEL_W = 240;
    private static final int ROW_H = 18;
    private static final int FRAME_THICK = 6;

    private final int slot;
    private final Screen parent;
    private final List<Object> rows = new ArrayList<>();
    private double scroll;
    private int listTop;
    private int listBottom;

    public AbilitySlotPickerScreen(int slot, Screen parent) {
        super(Component.translatable("townstead.ability.picker.title", slot));
        this.slot = slot;
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();
        // Grouped by source, in the order the server sent them, so a heading only ever appears once
        // and the arrangement is stable between opens.
        Map<String, List<AbilityLoadoutS2CPayload.Option>> bySource = new LinkedHashMap<>();
        for (AbilityLoadoutS2CPayload.Option option : ClientAbilityLoadout.available()) {
            bySource.computeIfAbsent(option.source(), key -> new ArrayList<>()).add(option);
        }
        for (Map.Entry<String, List<AbilityLoadoutS2CPayload.Option>> group : bySource.entrySet()) {
            rows.add(group.getKey());
            rows.addAll(group.getValue());
        }

        int buttonW = PANEL_W - 2 * 14;
        int buttonX = (width - buttonW) / 2;
        addRenderableWidget(new ParchmentButton(buttonX, height - 52, buttonW, 18,
                Component.translatable("townstead.ability.picker.clear"), button -> {
            Map<Integer, ResourceLocation> arrangement = ClientAbilityLoadout.arrangement();
            arrangement.remove(slot);
            send(arrangement);
            onClose();
        }));
        addRenderableWidget(new ParchmentButton(buttonX, height - 30, buttonW, 18,
                CommonComponents.GUI_DONE, button -> onClose()));
    }

    /**
     * The panel IS the background. Without this override the vanilla one runs inside
     * {@code super.render} and re-blurs over everything already drawn, which is the same order trap
     * every screen in this family has hit.
     */
    //? if >=1.21 {
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawPanel(g);
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics g) {
        drawPanel(g);
    }
    *///?}

    private void drawPanel(GuiGraphics g) {
        int left = (width - PANEL_W) / 2;
        int top = 24;
        int bottom = height - 60;
        g.fill(0, 0, width, height, 0x88070402);
        FrameRenderer.drawWoodenFrame(g, left, top, PANEL_W, bottom - top, FRAME_THICK);
        g.fill(left, top, left + PANEL_W, bottom, Palette.DESK_DEEP);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = (width - PANEL_W) / 2;
        int top = 24;
        int bottom = height - 60;
        listTop = top + 20;
        listBottom = bottom;

        //? if >=1.21 {
        super.render(g, mouseX, mouseY, partialTick);
        //?} else {
        /*renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        *///?}

        String title = getTitle().getString();
        g.drawString(font, title, left + (PANEL_W - font.width(title)) / 2, top + 6,
                Palette.BRASS, false);
        g.fill(left + 4, top + 18, left + PANEL_W - 4, top + 19, Palette.DESK_LIP);

        if (rows.isEmpty()) {
            // An empty box says "broken". Saying WHY it is empty is the difference between a bug
            // report and an answer, and the usual reason is that passive abilities take no slot.
            int ty = listTop + 8;
            for (net.minecraft.util.FormattedCharSequence line : font.split(
                    Component.translatable("townstead.ability.picker.empty"), PANEL_W - 28)) {
                g.drawString(font, line, left + 14, ty, 0xFF8A7654, false);
                ty += font.lineHeight + 3;
            }
            return;
        }

        ResourceLocation current = ClientAbilityLoadout.arrangement().get(slot);
        g.enableScissor(left, listTop, left + PANEL_W, listBottom);
        int y = listTop + 2 - (int) scroll;
        for (Object row : rows) {
            if (y > listBottom) break;
            if (y + ROW_H >= listTop) drawRow(g, row, left, y, mouseX, mouseY, current);
            y += rowHeight(row);
        }
        g.disableScissor();

        int content = contentHeight();
        int view = listBottom - listTop;
        if (content > view) {
            int trackX = left + PANEL_W - 4;
            g.fill(trackX, listTop, trackX + 2, listBottom, 0x33000000);
            int thumb = Math.max(14, view * view / content);
            int thumbY = listTop + (int) ((view - thumb) * (scroll / (content - view)));
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumb, 0x99705A38);
        }
    }

    private void drawRow(GuiGraphics g, Object row, int left, int y, int mouseX, int mouseY,
                         ResourceLocation current) {
        if (row instanceof String heading) {
            g.drawString(font, heading, left + 8, y + 3, Palette.BRASS_DEEP, false);
            return;
        }
        AbilityLoadoutS2CPayload.Option option = (AbilityLoadoutS2CPayload.Option) row;
        boolean chosen = current != null && current.toString().equals(option.id());
        boolean hover = mouseX >= left + 6 && mouseX < left + PANEL_W - 6
                && mouseY >= y && mouseY < y + ROW_H - 2 && mouseY >= listTop && mouseY < listBottom;
        if (chosen || hover) {
            g.fill(left + 6, y, left + PANEL_W - 6, y + ROW_H - 2, chosen ? 0xFF3A2611 : 0xFF2E1F0C);
        }
        if (chosen) g.fill(left + 6, y, left + 8, y + ROW_H - 2, Palette.BRASS);
        ItemStack icon = iconOf(option.icon());
        if (!icon.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(left + 11, y + 1, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
        } else {
            String mark = com.aetherianartificer.townstead.root.ability.AbilityNames
                    .initialsOf(option.name());
            g.drawString(font, mark, left + 12 - font.width(mark) / 2, y + 3,
                    chosen ? Palette.BRASS : 0xFF8A7048, false);
        }
        g.drawString(font, option.name(), left + 26, y + 3,
                chosen ? 0xFFF0DDB0 : 0xFFC0AC85, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || mouseY < listTop || mouseY >= listBottom) return false;
        int left = (width - PANEL_W) / 2;
        if (mouseX < left + 6 || mouseX >= left + PANEL_W - 6) return false;
        int y = listTop + 2 - (int) scroll;
        for (Object row : rows) {
            int h = rowHeight(row);
            if (row instanceof AbilityLoadoutS2CPayload.Option option
                    && mouseY >= y && mouseY < y + ROW_H - 2) {
                ResourceLocation id = ResourceLocation.tryParse(option.id());
                if (id == null) return false;
                Map<Integer, ResourceLocation> arrangement = ClientAbilityLoadout.arrangement();
                // An ability lives in one slot. Putting it here takes it out of wherever it was,
                // rather than leaving the same thing on two keys with one of them a lie.
                arrangement.values().removeIf(id::equals);
                arrangement.put(slot, id);
                send(arrangement);
                onClose();
                return true;
            }
            y += h;
        }
        return false;
    }

    //? if >=1.21 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scrollBy(deltaY);
        return true;
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollBy(delta);
        return true;
    }
    *///?}

    private void scrollBy(double delta) {
        int max = Math.max(0, contentHeight() - (listBottom - listTop));
        scroll = Mth.clamp(scroll - delta * 12, 0, max);
    }

    private int contentHeight() {
        int total = 4;
        for (Object row : rows) total += rowHeight(row);
        return total;
    }

    private static int rowHeight(Object row) {
        return row instanceof String ? 14 : ROW_H;
    }

    private static ItemStack iconOf(String icon) {
        if (icon.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(icon);
        if (id == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void send(Map<Integer, ResourceLocation> arrangement) {
        AbilityLoadoutC2SPayload payload = new AbilityLoadoutC2SPayload(Map.copyOf(arrangement));
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }
}
