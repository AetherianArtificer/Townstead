package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Shared multi-select dropdown: a checkbox per row, a first row that checks or clears them all, and
 * a scrollbar once the rows outgrow the room. Clicking a row toggles it and keeps the menu open; Esc
 * or a click outside closes it. Its owner dispatches input to it before the screen beneath.
 */
public final class CheckMenu {
    private static final int BOX = 9;
    private static final int MAX_ROWS = 10;

    private List<Component> labels = List.of();
    private Predicate<Integer> checked = i -> false;
    private BiConsumer<Integer, Boolean> toggle = (i, on) -> {};
    private Controls.Rect bounds;
    private int visible;
    private int first;
    private int highlighted;

    public boolean open() {
        return !labels.isEmpty();
    }

    public void close() {
        labels = List.of();
    }

    /**
     * Opens below x, y. Row 0 is "all": its box is on when every row is, and toggling it sets them
     * all. {@code toggle} gets each row's index (from 0) and its new state.
     */
    public void open(Font font, Component all, List<Component> rows, Predicate<Integer> checked,
                     BiConsumer<Integer, Boolean> toggle, int x, int y, int minW, int screenW, int screenH) {
        java.util.ArrayList<Component> list = new java.util.ArrayList<>();
        list.add(all);
        list.addAll(rows);
        this.labels = List.copyOf(list);
        this.checked = checked;
        this.toggle = toggle;
        int textW = labels.stream().mapToInt(font::width).max().orElse(60);
        int w = Math.min(screenW - 8, Math.max(minW, MenuPanel.LABEL_X + textW + 12));
        visible = Math.min(labels.size(), Math.min(MAX_ROWS, MenuPanel.fit(screenH - y - 8, false)));
        int h = MenuPanel.height(visible, false);
        bounds = new Controls.Rect(Math.max(4, Math.min(x, screenW - w - 4)), Math.max(4, Math.min(y, screenH - h - 4)), w, h);
        first = 0;
        highlighted = 0;
        narrate();
    }

    private boolean isChecked(int row) {
        if (row == 0) {
            for (int i = 1; i < labels.size(); i++) if (!checked.test(i - 1)) return false;
            return true;
        }
        return checked.test(row - 1);
    }

    private void flip(int row) {
        boolean on = !isChecked(row);
        if (row == 0) {
            for (int i = 1; i < labels.size(); i++) toggle.accept(i - 1, on);
        } else {
            toggle.accept(row - 1, on);
        }
        narrate();
    }

    public void render(GuiGraphics g, Font font, int mx, int my) {
        if (!open()) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        MenuPanel.drawFrame(g, font, bounds.x(), bounds.y(), bounds.w(), bounds.h(), null, false);
        for (int i = 0; i < visible; i++) {
            int row = first + i;
            int y = MenuPanel.rowsTop(bounds.y(), false) + i * MenuPanel.ROW_H;
            boolean hover = new Controls.Rect(bounds.x(), y, bounds.w(), MenuPanel.ROW_H).contains(mx, my);
            MenuPanel.drawRow(g, bounds.x(), y, bounds.w(), row == highlighted, hover);
            boolean on = isChecked(row);
            int bx = bounds.x() + MenuPanel.ICON_X;
            int by = y + (MenuPanel.ROW_H - 1 - BOX) / 2;
            Palette.drawOutline(g, bx, by, bx + BOX, by + BOX, Palette.BRASS_DEEP);
            g.fill(bx + 1, by + 1, bx + BOX - 1, by + BOX - 1, 0xFF140F08);
            if (on) g.fill(bx + 2, by + 2, bx + BOX - 2, by + BOX - 2, Palette.BRASS);
            g.drawString(font, labels.get(row), bounds.x() + MenuPanel.LABEL_X, y + MenuPanel.TEXT_Y,
                    MenuPanel.labelColor(on), false);
            if (row == 0 && labels.size() > 1) {
                g.fill(bounds.x() + 4, y + MenuPanel.ROW_H - 1, bounds.x() + bounds.w() - 4, y + MenuPanel.ROW_H, Palette.MENU_RULE);
            }
        }
        MenuPanel.drawScrollbar(g, bounds.x(), bounds.y(), bounds.w(), bounds.h(), false, first, visible, labels.size());
        g.pose().popPose();
    }

    public boolean click(double x, double y, int button) {
        if (!open()) return false;
        if (!bounds.contains(x, y)) {
            close();
            return true;
        }
        int local = MenuPanel.rowAt(x, y, bounds.x(), bounds.y(), bounds.w(), false, visible);
        if (button == 0 && local >= 0) {
            highlighted = first + local;
            flip(highlighted);
        }
        return true;
    }

    public boolean scroll(double x, double y, double amount) {
        if (!open() || !bounds.contains(x, y)) return false;
        first = Math.max(0, Math.min(labels.size() - visible, first - (int) Math.signum(amount)));
        return true;
    }

    public boolean key(int key) {
        if (!open()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_TAB) {
            close();
        } else if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            highlighted = Math.floorMod(highlighted + (key == GLFW.GLFW_KEY_UP ? -1 : 1), labels.size());
            if (highlighted < first) first = highlighted;
            if (highlighted >= first + visible) first = highlighted - visible + 1;
            narrate();
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_KP_ENTER) {
            flip(highlighted);
        }
        return true;
    }

    private void narrate() {
        Minecraft.getInstance().getNarrator().sayNow(Component.translatable(
                isChecked(highlighted) ? "townstead.menu.checked" : "townstead.menu.unchecked", labels.get(highlighted)));
    }
}
