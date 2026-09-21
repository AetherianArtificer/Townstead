package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.function.IntConsumer;

/** Shared dropdown overlay. Its owner dispatches input before the canvas beneath it. */
public final class CatalogMenu {
    private List<Component> choices = List.of();
    private IntConsumer select;
    private Controls.Rect bounds;
    private int highlighted;
    public boolean open() { return !choices.isEmpty(); }
    public void close() { choices = List.of(); }
    public void open(Font font, List<Component> labels, int current, int x, int y, int screenW, int screenH, IntConsumer action) {
        choices = List.copyOf(labels); select = action; highlighted = Math.max(0, current);
        int w = Math.min(screenW - 8, labels.stream().mapToInt(font::width).max().orElse(80) + 20);
        int h = MenuPanel.height(labels.size(), false);
        bounds = new Controls.Rect(Math.max(4, Math.min(x, screenW - w - 4)), Math.max(4, Math.min(y, screenH - h - 4)), w, h);
        narrate();
    }
    public void render(GuiGraphics g, Font font, int mx, int my) {
        if (!open()) return;
        g.pose().pushPose(); g.pose().translate(0, 0, 400);
        MenuPanel.drawFrame(g, font, bounds.x(), bounds.y(), bounds.w(), bounds.h(), null, false);
        for (int i = 0; i < choices.size(); i++) {
            int y = MenuPanel.rowsTop(bounds.y(), false) + i * MenuPanel.ROW_H;
            MenuPanel.drawRow(g, bounds.x(), y, bounds.w(), i == highlighted,
                    new Controls.Rect(bounds.x(), y, bounds.w(), MenuPanel.ROW_H).contains(mx, my));
            g.drawString(font, choices.get(i), bounds.x() + 6, y + MenuPanel.TEXT_Y, Palette.TAB_ACTIVE, false);
        }
        g.pose().popPose();
    }
    public boolean click(double x, double y, int button) {
        if (!open()) return false;
        int index = MenuPanel.rowAt(x, y, bounds.x(), bounds.y(), bounds.w(), false, choices.size());
        boolean choose = button == 0 && bounds.contains(x, y) && index >= 0 && index < choices.size();
        close(); if (choose) select.accept(index);
        return true;
    }
    public boolean key(int key) {
        if (!open()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_TAB) close();
        else if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            highlighted = Math.floorMod(highlighted + (key == GLFW.GLFW_KEY_UP ? -1 : 1), choices.size()); narrate();
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_KP_ENTER) {
            int index = highlighted; close(); select.accept(index);
        }
        return true;
    }
    private void narrate() { Minecraft.getInstance().getNarrator().sayNow(choices.get(highlighted)); }
}
