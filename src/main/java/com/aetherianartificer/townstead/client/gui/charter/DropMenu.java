package com.aetherianartificer.townstead.client.gui.charter;

import com.aetherianartificer.townstead.client.gui.common.BookRenderer;
import com.aetherianartificer.townstead.client.gui.common.Controls;
import com.aetherianartificer.townstead.client.gui.common.MenuPanel;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A popover list opened from a control: the change menu, a successor, a culture, a colour. */
final class DropMenu {
    /** Something small drawn in a row's icon box, left of its label. */
    @FunctionalInterface interface Icon { void draw(GuiGraphics g, int x, int y); }

    /** {@code swatch} is an ARGB square drawn before the label, or 0 for none; {@code icon} replaces it. */
    record Entry(Component label, Runnable action, boolean danger, boolean chosen, int swatch, @Nullable Icon icon) {
        Entry(Component label, Runnable action) { this(label, action, false, false, 0, null); }
        Entry(Component label, Runnable action, boolean danger, boolean chosen, int swatch) { this(label, action, danger, chosen, swatch, null); }
        boolean marked() { return swatch != 0 || icon != null; }
    }

    private static final int PAD_X = 8;
    private static final int DANGER = 0xFFE07A62;
    private static final int HEAD_BAND = 0xFF160E05;
    private static final int DANGER_HOT = 0xFFFFA890;

    private final Font font;
    private final @Nullable String heading;
    private final List<Entry> entries;
    private final int x, y, w, h, visible;
    private int scroll;

    /**
     * Opens against {@code anchor}: above it when {@code up}, else below. It takes the room it
     * needs and scrolls past what {@code room} allows.
     */
    DropMenu(Font font, @Nullable Component heading, List<Entry> entries, Controls.Rect anchor, boolean up, int room, int screenW) {
        this.font = font;
        this.heading = heading == null ? null : heading.getString();
        this.entries = List.copyOf(entries);
        boolean swatches = entries.stream().anyMatch(Entry::marked);
        int widest = this.heading == null ? 0 : font.width(this.heading) + 10;
        for (Entry entry : entries) widest = Math.max(widest, font.width(entry.label()) + labelX(swatches) + PAD_X);
        this.w = Mth.clamp(Math.max(anchor.w(), widest), 60, Math.max(60, screenW - 16));
        boolean headed = this.heading != null;
        this.visible = Math.min(entries.size(), MenuPanel.fit(room, headed));
        this.h = MenuPanel.height(Math.max(1, visible), headed);
        this.x = Mth.clamp(anchor.x(), 8, Math.max(8, screenW - 8 - w));
        this.y = up ? anchor.y() - h - 2 : anchor.bottom() + 2;
    }

    private static int labelX(boolean swatches) {
        return swatches ? MenuPanel.LABEL_X : PAD_X;
    }

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Runs the entry under the pointer. True when the click landed on the menu at all. */
    boolean click(double mx, double my) {
        if (!contains(mx, my)) return false;
        int row = MenuPanel.rowAt(mx, my, x, y, w, heading != null, visible);
        if (row >= 0 && row + scroll < entries.size()) entries.get(row + scroll).action().run();
        return true;
    }

    void scroll(double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, entries.size() - visible));
    }

    void draw(GuiGraphics g, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        MenuPanel.drawFrame(g, font, x, y, w, h, null, false);
        boolean swatches = entries.stream().anyMatch(Entry::marked);
        if (heading != null) {
            // A darker band, its text on the rows' own left edge, so the heading reads as the menu's title.
            g.fill(x + 1, y + 1, x + w - 1, y + MenuPanel.HEAD_H - 1, HEAD_BAND);
            g.fill(x + 1, y + MenuPanel.HEAD_H - 1, x + w - 1, y + MenuPanel.HEAD_H, Palette.MENU_EDGE);
            g.drawString(font, BookRenderer.fit(font, heading, w - labelX(swatches) - PAD_X), x + labelX(swatches),
                    y + MenuPanel.TEXT_Y, Palette.LABEL_WARM, false);
        }
        int top = MenuPanel.rowsTop(y, heading != null);
        if (entries.isEmpty()) {
            g.pose().popPose();
            return;
        }
        for (int local = 0; local < visible; local++) {
            int i = local + scroll;
            if (i >= entries.size()) break;
            Entry entry = entries.get(i);
            int ry = top + local * MenuPanel.ROW_H;
            boolean hot = mx >= x && mx < x + w && my >= ry && my < ry + MenuPanel.ROW_H;
            MenuPanel.drawRow(g, x, ry, w, entry.chosen(), hot);
            if (entry.icon() != null) {
                entry.icon().draw(g, x + MenuPanel.ICON_X, ry + 1);
            } else if (entry.swatch() != 0) {
                g.fill(x + MenuPanel.ICON_X, ry + 3, x + MenuPanel.ICON_X + 8, ry + 11, entry.swatch());
                Palette.drawOutline(g, x + MenuPanel.ICON_X, ry + 3, x + MenuPanel.ICON_X + 8, ry + 11, Palette.MENU_EDGE);
            }
            int color = entry.danger() ? (hot ? DANGER_HOT : DANGER) : hot ? Palette.LABEL_LIGHT : MenuPanel.labelColor(entry.chosen());
            int lx = x + labelX(swatches);
            g.drawString(font, BookRenderer.fit(font, entry.label().getString(), w - (lx - x) - PAD_X), lx, ry + MenuPanel.TEXT_Y, color, false);
        }
        MenuPanel.drawScrollbar(g, x, y, w, h, heading != null, scroll, visible, entries.size());
        g.pose().popPose();
    }
}
