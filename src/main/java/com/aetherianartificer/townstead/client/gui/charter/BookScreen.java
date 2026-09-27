package com.aetherianartificer.townstead.client.gui.charter;

import com.aetherianartificer.townstead.client.gui.common.BookRenderer;
import com.aetherianartificer.townstead.client.gui.common.Controls;
import com.aetherianartificer.townstead.client.gui.common.FrameRenderer;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static com.aetherianartificer.townstead.client.gui.common.BookRenderer.*;

/**
 * A book open on a desk: folder tabs on its top edge, two pages set on {@link BookRenderer}'s
 * grid, and a bar of buttons on the desk below. Screens compose their pages as draw steps and
 * page zones; the book, the desk and any open menu are drawn here.
 */
abstract class BookScreen extends Screen {
    protected static final int TARGET_W = 640, TARGET_H = 400;
    /** The desk showing inside the frame, the button bar, and the gap between book and bar. */
    protected static final int EDGE = 12, BAR_H = 18, BAR_GAP = 8;

    protected int left, top, panelW, panelH, barY, tab, mouseX, mouseY;
    protected Controls.Rect book, leftPage, rightPage;
    protected @Nullable DropMenu menu;
    private int statusLeft, statusRight;
    private final List<Consumer<GuiGraphics>> ops = new ArrayList<>();
    private final List<PageZone> tabs = new ArrayList<>();
    private final List<String> tabLabels = new ArrayList<>();

    protected BookScreen(Component title) { super(title); }

    @Override protected void init() {
        panelW = Math.min(TARGET_W, width - 16);
        panelH = Math.min(TARGET_H, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        int bookTop = top + EDGE + TAB_H;
        barY = top + panelH - EDGE - BAR_H;
        book = new Controls.Rect(left + EDGE, bookTop, panelW - 2 * EDGE, barY - BAR_GAP - bookTop);
        Controls.Rect[] pages = BookRenderer.pages(book);
        leftPage = pages[0];
        rightPage = pages[1];
        rebuildWidgets();
    }

    @Override protected final void rebuildWidgets() {
        clearWidgets();
        ops.clear();
        tabs.clear();
        tabLabels.clear();
        statusLeft = statusRight = 0;
        if (book != null) compose();
    }

    /** Adds this state's zones, fields and bar buttons, and queues its page drawing. */
    protected abstract void compose();

    /** The line of news the bar shows between its button groups. */
    protected String status() { return ""; }

    /** Things that sit on the desk rather than the page. */
    protected void drawDesk(GuiGraphics g) {}

    /** Things held above everything else, like a stamp in hand. */
    protected void drawOverlay(GuiGraphics g, int mx, int my) {}

    /** A click this screen wants before the widgets see it. */
    protected boolean clicked(double mx, double my, int button) { return false; }

    protected boolean scrolled(double mx, double my, double delta) { return false; }

    /** Esc handling before the screen closes: true when something was put away instead. */
    protected boolean escape() { return false; }

    // ── Composition ──

    protected void op(Consumer<GuiGraphics> draw) { ops.add(draw); }

    protected PageZone zone(Controls.Rect rect, Component narration, Runnable action) {
        return addRenderableWidget(new PageZone(rect, narration, action));
    }

    protected static int x(Controls.Rect page) { return BookRenderer.left(page); }
    protected static int w(Controls.Rect page) { return BookRenderer.width(page); }
    protected static int y(Controls.Rect page) { return BookRenderer.top(page); }

    /** Folder tabs along the left page's top edge. {@code enabled} false leaves them showing but inert. */
    protected void composeTabs(List<Component> labels, IntConsumer select, boolean enabled) {
        int x = x(leftPage);
        for (int i = 0; i < labels.size(); i++) {
            int index = i;
            String label = BookRenderer.fit(font, labels.get(i).getString(), 160);
            int w = BookRenderer.tabWidth(font, label);
            PageZone zone = zone(new Controls.Rect(x, book.y() - TAB_H, w, TAB_H), labels.get(i), () -> {
                if (tab != index) select.accept(index);
            });
            zone.active = enabled || index == tab;
            tabs.add(zone);
            tabLabels.add(label);
            x += w + 2;
        }
    }

    protected void runningHead(Controls.Rect page, Component text, boolean right) {
        String value = text.getString();
        op(g -> BookRenderer.runningHead(g, font, page, value, right));
    }

    protected int heading(Component text, int x, int y, int w) {
        String value = text.getString();
        op(g -> BookRenderer.heading(g, font, value, x, y, w));
        return y + HEADING;
    }

    /**
     * A dot-leader row. With {@code edit}, its value sits on a dotted line and clicking it hands
     * the value's rectangle over, so a menu can open against it.
     */
    protected int row(Component label, Component value, int x, int y, int w, int color, @Nullable Consumer<Controls.Rect> edit) {
        rowZone(label, value, x, y, w, color, edit);
        return y + ROW;
    }

    protected @Nullable PageZone rowZone(Component label, Component value, int x, int y, int w, int color,
                                         @Nullable Consumer<Controls.Rect> edit) {
        String l = label.getString(), v = value.getString();
        int valueW = font.width(BookRenderer.fit(font, v, Math.max(w / 2, w - font.width(l) - 12)));
        Controls.Rect valueRect = new Controls.Rect(x + w - valueW - 2, y, valueW + 4, ROW);
        PageZone zone = edit == null ? null : zone(valueRect, label, () -> edit.accept(valueRect));
        op(g -> {
            BookRenderer.leader(g, font, l, v, x, y, w, INK, color);
            if (zone != null && zone.active) BookRenderer.line(g, x + w - valueW, y + TEXT_Y + 9, valueW, zone.hot(), false);
        });
        return zone;
    }

    protected int plain(Component text, int x, int y, int w, int color) {
        String value = text.getString();
        op(g -> g.drawString(font, BookRenderer.fit(font, value, w), x, y + TEXT_Y, color, false));
        return y + ROW;
    }

    /**
     * A detail line under a row, wrapping once before it is cut. It stands {@link BookRenderer#DETAIL_GAP}
     * below the row so the pair reads as one entry without the detail crowding the row's text.
     */
    protected int detail(Component text, int x, int y, int w, int color) {
        int lines = BookRenderer.lines(font, text, w, 2);
        if (lines == 0) return y;
        int top = y + DETAIL_GAP;
        op(g -> BookRenderer.wrap(g, font, text, x, top, w, color, 2, false));
        return top + lines * DETAIL;
    }

    protected int centredDetail(Component text, int x, int y, int w) {
        int lines = BookRenderer.lines(font, text, w, 2);
        op(g -> BookRenderer.wrap(g, font, text, x, y, w, FADED, 2, true));
        return y + lines * DETAIL;
    }

    /** The page's foot line, set from the bottom margin up; returns its top. */
    protected int foot(Controls.Rect page, Component text, int color) {
        int w = w(page);
        int lines = BookRenderer.lines(font, text, w, 2);
        int top = BookRenderer.foot(page) - Math.max(0, lines - 1) * DETAIL;
        if (lines > 0) op(g -> BookRenderer.wrap(g, font, text, x(page), top, w, color, 2, false));
        return top;
    }

    /** Lays out the desk's buttons: a group from the left, a group from the right, news between. */
    protected final class Bar {
        private int x = left + EDGE;
        private int right = left + panelW - EDGE;

        CharterButton left(Component label, Consumer<Controls.Rect> action) {
            int w = width(label);
            CharterButton button = place(x, w, label, action);
            x += w + 4;
            statusLeft = x + 4;
            return button;
        }

        CharterButton left(Component label, Runnable action) { return left(label, rect -> action.run()); }

        CharterButton right(Component label, Consumer<Controls.Rect> action) {
            int w = width(label);
            right -= w;
            CharterButton button = place(right, w, label, action);
            right -= 4;
            statusRight = right - 4;
            return button;
        }

        CharterButton right(Component label, Runnable action) { return right(label, rect -> action.run()); }

        /** Space on the right for something that is not a button. */
        Controls.Rect reserveRight(int w, int h) {
            right -= 8 + w;
            Controls.Rect rect = new Controls.Rect(right, barY + BAR_H - h, w, h);
            statusRight = right - 8;
            return rect;
        }

        private int width(Component label) { return Math.max(48, font.width(label) + 16); }

        private CharterButton place(int bx, int w, Component label, Consumer<Controls.Rect> action) {
            Controls.Rect rect = new Controls.Rect(bx, barY, w, BAR_H);
            return addRenderableWidget(new CharterButton(bx, barY, w, BAR_H, label, v -> action.accept(rect)));
        }
    }

    /** A menu opening upward from the bar, with the room between the bar and the frame's top. */
    protected DropMenu menuAbove(@Nullable Component heading, List<DropMenu.Entry> entries, Controls.Rect anchor) {
        return new DropMenu(font, heading, entries, anchor, true, anchor.y() - top - EDGE, width);
    }

    protected DropMenu menuBelow(@Nullable Component heading, List<DropMenu.Entry> entries, Controls.Rect anchor) {
        return new DropMenu(font, heading, entries, anchor, false, barY - anchor.bottom() - 4, width);
    }

    // ── Rendering ──

    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        mouseX = mx;
        mouseY = my;
        //? if >=1.21 {
        super.render(g, mx, my, partialTick);
        //?} else {
        /*renderBackground(g);
        super.render(g, mx, my, partialTick);
        *///?}
        drawOverlay(g, mx, my);
        if (menu != null) menu.draw(g, mx, my);
    }

    //? if >=1.21 {
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) { drawScene(g); }
    //?} else {
    /*@Override public void renderBackground(GuiGraphics g) { drawScene(g); }
    *///?}

    protected void drawScene(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xA0100C08);
        FrameRenderer.drawInnerPanel(g, left, top, panelW, panelH);
        FrameRenderer.drawWoodenFrame(g, left, top, panelW, panelH, 6);
        if (book == null) return;
        for (int i = 0; i < tabs.size(); i++) {
            if (i != tab) drawTab(g, i);
        }
        BookRenderer.draw(g, book);
        if (tab < tabs.size()) drawTab(g, tab);
        ops.forEach(op -> op.accept(g));
        drawDesk(g);
        String status = status();
        int room = statusRight - statusLeft;
        if (!status.isBlank() && room > 30) {
            String shown = BookRenderer.fit(font, status, room);
            g.drawString(font, shown, statusRight - font.width(shown), barY + 5, Palette.LABEL_WARM, false);
        }
    }

    private void drawTab(GuiGraphics g, int index) {
        PageZone zone = tabs.get(index);
        BookRenderer.tab(g, font, new Controls.Rect(zone.getX(), zone.getY(), zone.getWidth(), zone.getHeight()),
                leftPage.y() + 1, tabLabels.get(index), index == tab, zone.active && zone.hot());
    }

    // ── Input ──

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (menu != null) {
            if (!menu.click(mx, my)) menu = null;
            return true;
        }
        return clicked(mx, my, button) || super.mouseClicked(mx, my, button);
    }

    //? if >=1.21 {
    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return wheel(mx, my, dy) || super.mouseScrolled(mx, my, dx, dy);
    }
    //?} else {
    /*@Override public boolean mouseScrolled(double mx, double my, double delta) {
        return wheel(mx, my, delta) || super.mouseScrolled(mx, my, delta);
    }
    *///?}

    private boolean wheel(double mx, double my, double delta) {
        if (menu != null && menu.contains(mx, my)) { menu.scroll(delta); return true; }
        return scrolled(mx, my, delta);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (menu != null) { menu = null; return true; }
            if (escape()) return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }
}
