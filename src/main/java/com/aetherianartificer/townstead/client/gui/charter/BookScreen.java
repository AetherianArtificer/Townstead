package com.aetherianartificer.townstead.client.gui.charter;

import com.aetherianartificer.townstead.client.gui.common.BookRenderer;
import com.aetherianartificer.townstead.client.gui.common.Controls;
import com.aetherianartificer.townstead.client.gui.common.FrameRenderer;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.client.gui.common.UiScissor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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

    /** A window this small, in real GUI units, cannot hold the book at full size. */
    private static final int SHRINK_W = 560, SHRINK_H = 330;

    protected int left, top, panelW, panelH, barY, tab, mouseX, mouseY;
    /**
     * How many times smaller than the GUI scale the screen is drawn: 2 sets the whole book at half
     * size, so a high GUI scale leaves room for it. Everything is laid out in these smaller units.
     */
    protected int shrink = 1;
    protected Controls.Rect book, leftPage, rightPage;
    protected @Nullable DropMenu menu;
    private int statusLeft, statusRight;
    private record Op(int page, Consumer<GuiGraphics> draw) {}
    /** How far one wheel notch moves a page. */
    private static final int SCROLL_STEP = 12;
    private static final int NO_EXTENT = Integer.MIN_VALUE;

    private final List<Op> ops = new ArrayList<>();
    /** Per page (left, right): how far its content is scrolled, how low it reaches, and where its foot line starts. */
    private final int[] scroll = new int[2];
    private final int[] extent = {NO_EXTENT, NO_EXTENT};
    private final int[] footTop = {NO_EXTENT, NO_EXTENT};
    private int current = -1;
    private final List<PageZone> tabs = new ArrayList<>();
    private final List<String> tabLabels = new ArrayList<>();

    protected BookScreen(Component title) { super(title); }

    @Override protected void init() {
        layout();
        rebuildWidgets();
    }

    // Newer versions resize without running init again.
    @Override public void resize(net.minecraft.client.Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        layout();
        rebuildWidgets();
    }

    /** The screen's width and height in the units it is laid out in. */
    protected final int viewW() { return width * shrink; }
    protected final int viewH() { return height * shrink; }

    /** Half size only where it stays pixel-exact: an even GUI scale, in a window too small for full size. */
    private int shrinkFor() {
        if (minecraft == null) return 1;
        double scale = minecraft.getWindow().getGuiScale();
        boolean even = scale >= 2 && scale % 2 == 0;
        return even && (width - 16 < SHRINK_W || height - 16 < SHRINK_H) ? 2 : 1;
    }

    private void layout() {
        shrink = shrinkFor();
        panelW = Math.min(TARGET_W, viewW() - 16);
        panelH = Math.min(TARGET_H, viewH() - 16);
        left = (viewW() - panelW) / 2;
        top = (viewH() - panelH) / 2;
        int bookTop = top + EDGE + TAB_H;
        barY = top + panelH - EDGE - BAR_H;
        book = new Controls.Rect(left + EDGE, bookTop, panelW - 2 * EDGE, barY - BAR_GAP - bookTop);
        Controls.Rect[] pages = BookRenderer.pages(book);
        leftPage = pages[0];
        rightPage = pages[1];
    }

    @Override protected final void rebuildWidgets() {
        if (book == null) {
            clearWidgets();
            return;
        }
        // A page that shrank may be scrolled past its end: pull it back and set it again.
        for (int pass = 0; pass < 2; pass++) {
            clearWidgets();
            ops.clear();
            tabs.clear();
            tabLabels.clear();
            statusLeft = statusRight = 0;
            extent[0] = extent[1] = footTop[0] = footTop[1] = NO_EXTENT;
            current = -1;
            compose();
            boolean moved = false;
            for (int i = 0; i < 2; i++) {
                int max = maxScroll(i);
                if (scroll[i] > max) { scroll[i] = max; moved = true; }
            }
            if (!moved) break;
        }
        hideClipped();
    }

    /** Whether each page scrolls its content when it is taller than the page. */
    protected boolean pagesScroll() { return false; }

    /** Puts both pages back at their tops. */
    protected final void resetScroll() { scroll[0] = scroll[1] = 0; }

    /** How far this page's content is scrolled up; add it to a laid-out y to get the unscrolled position. */
    protected final int offset(Controls.Rect page) { return pagesScroll() ? scroll[pageIndex(page)] : 0; }

    /** Marks that this page's content reaches down to {@code y}, for content drawn outside the row helpers. */
    protected final int reach(int y) {
        if (current >= 0) extent[current] = Math.max(extent[current], y);
        return y;
    }

    private int pageIndex(Controls.Rect page) { return page.equals(leftPage) ? 0 : 1; }

    private Controls.Rect page(int index) { return index == 0 ? leftPage : rightPage; }

    private int clipTop(int index) { return BookRenderer.top(page(index)) - 4; }

    private int clipBottom(int index) {
        return footTop[index] == NO_EXTENT ? BookRenderer.foot(page(index)) + 9 : footTop[index] - 2;
    }

    private int maxScroll(int index) {
        if (!pagesScroll() || extent[index] == NO_EXTENT) return 0;
        return Math.max(0, extent[index] + scroll[index] - clipBottom(index));
    }

    /** Widgets scrolled out of their page are neither drawn nor clickable. */
    private void hideClipped() {
        if (!pagesScroll()) return;
        for (var child : children()) {
            if (!(child instanceof AbstractWidget widget)) continue;
            int top = widget.getY(), bottom = top + widget.getHeight();
            if (bottom <= book.y() || top >= book.bottom()) continue;
            int index = widget.getX() + widget.getWidth() / 2 <= leftPage.right() ? 0 : 1;
            if (top >= clipTop(index) && bottom <= clipBottom(index)) continue;
            widget.visible = false;
            widget.active = false;
        }
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

    protected void op(Consumer<GuiGraphics> draw) { ops.add(new Op(pagesScroll() ? current : -1, draw)); }

    /** Drawing that stays put while a page scrolls: running heads and foot lines. */
    private void fixedOp(Consumer<GuiGraphics> draw) { ops.add(new Op(-1, draw)); }

    protected PageZone zone(Controls.Rect rect, Component narration, Runnable action) {
        reach(rect.bottom());
        return addRenderableWidget(new PageZone(rect, narration, action));
    }

    protected static int x(Controls.Rect page) { return BookRenderer.left(page); }
    protected static int w(Controls.Rect page) { return BookRenderer.width(page); }

    /** Where a page's content starts, moved up by its scroll. Everything on the page is laid out from here. */
    protected int y(Controls.Rect page) {
        current = pageIndex(page);
        return BookRenderer.top(page) - offset(page);
    }

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
        fixedOp(g -> BookRenderer.runningHead(g, font, page, value, right));
    }

    protected int heading(Component text, int x, int y, int w) {
        String value = text.getString();
        op(g -> BookRenderer.heading(g, font, value, x, y, w));
        return reach(y + HEADING);
    }

    /**
     * A dot-leader row. With {@code edit}, its value sits on a dotted line and clicking it hands
     * the value's rectangle over, so a menu can open against it.
     */
    protected int row(Component label, Component value, int x, int y, int w, int color, @Nullable Consumer<Controls.Rect> edit) {
        rowZone(label, value, x, y, w, color, edit);
        return reach(y + ROW);
    }

    protected @Nullable PageZone rowZone(Component label, Component value, int x, int y, int w, int color,
                                         @Nullable Consumer<Controls.Rect> edit) {
        String l = label.getString(), v = value.getString();
        int valueW = font.width(BookRenderer.fit(font, v, Math.max(w / 2, w - font.width(l) - 12)));
        Controls.Rect valueRect = new Controls.Rect(x + w - valueW - 2, y, valueW + 4, ROW);
        PageZone zone = edit == null ? null : zone(valueRect, label, () -> edit.accept(valueRect));
        reach(y + ROW);
        op(g -> {
            BookRenderer.leader(g, font, l, v, x, y, w, INK, color);
            if (zone != null && zone.active) BookRenderer.line(g, x + w - valueW, y + TEXT_Y + 9, valueW, zone.hot(), false);
        });
        return zone;
    }

    protected int plain(Component text, int x, int y, int w, int color) {
        String value = text.getString();
        op(g -> g.drawString(font, BookRenderer.fit(font, value, w), x, y + TEXT_Y, color, false));
        return reach(y + ROW);
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
        return reach(top + lines * DETAIL);
    }

    protected int centredDetail(Component text, int x, int y, int w) {
        return centredDetail(text, x, y, w, 2);
    }

    protected int centredDetail(Component text, int x, int y, int w, int maxLines) {
        int lines = BookRenderer.lines(font, text, w, maxLines);
        if (lines > 0) op(g -> BookRenderer.wrap(g, font, text, x, y, w, FADED, lines, true));
        return reach(y + lines * DETAIL);
    }

    /** The page's foot line, set from the bottom margin up; returns its top. */
    protected int foot(Controls.Rect page, Component text, int color) {
        int w = w(page);
        int lines = BookRenderer.lines(font, text, w, 2);
        int top = BookRenderer.foot(page) - Math.max(0, lines - 1) * DETAIL;
        if (lines > 0) fixedOp(g -> BookRenderer.wrap(g, font, text, x(page), top, w, color, 2, false));
        footTop[pageIndex(page)] = top;
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
        return new DropMenu(font, heading, entries, anchor, true, anchor.y() - top - EDGE, viewW());
    }

    protected DropMenu menuBelow(@Nullable Component heading, List<DropMenu.Entry> entries, Controls.Rect anchor) {
        return new DropMenu(font, heading, entries, anchor, false, barY - anchor.bottom() - 4, viewW());
    }

    // ── Rendering ──

    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        mx *= shrink;
        my *= shrink;
        mouseX = mx;
        mouseY = my;
        g.pose().pushPose();
        if (shrink > 1) g.pose().scale(1f / shrink, 1f / shrink, 1f);
        UiScissor.scale(1f / shrink);
        try {
            //? if >=1.21 {
            super.render(g, mx, my, partialTick);
            //?} else {
            /*renderBackground(g);
            super.render(g, mx, my, partialTick);
            *///?}
            drawOverlay(g, mx, my);
            if (menu != null) menu.draw(g, mx, my);
        } finally {
            UiScissor.scale(1f);
            g.pose().popPose();
        }
    }

    //? if >=1.21 {
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) { drawScene(g); }
    //?} else {
    /*@Override public void renderBackground(GuiGraphics g) { drawScene(g); }
    *///?}

    protected void drawScene(GuiGraphics g) {
        g.fill(0, 0, viewW(), viewH(), 0xA0100C08);
        FrameRenderer.drawInnerPanel(g, left, top, panelW, panelH);
        FrameRenderer.drawWoodenFrame(g, left, top, panelW, panelH, 6);
        if (book == null) return;
        for (int i = 0; i < tabs.size(); i++) {
            if (i != tab) drawTab(g, i);
        }
        BookRenderer.draw(g, book);
        if (tab < tabs.size()) drawTab(g, tab);
        // Whatever a page cannot fit is cut at the book's edge rather than spilling onto the desk.
        UiScissor.enable(g, book.x(), book.y(), book.right(), book.bottom());
        for (Op op : ops) {
            if (op.page() < 0) {
                op.draw().accept(g);
                continue;
            }
            Controls.Rect page = page(op.page());
            UiScissor.enable(g, page.x(), clipTop(op.page()), page.right(), clipBottom(op.page()));
            op.draw().accept(g);
            g.disableScissor();
        }
        for (int i = 0; i < 2; i++) drawScrollbar(g, i);
        g.disableScissor();
        drawDesk(g);
        String status = status();
        int room = statusRight - statusLeft;
        if (!status.isBlank() && room > 30) {
            String shown = BookRenderer.fit(font, status, room);
            g.drawString(font, shown, statusRight - font.width(shown), barY + 5, Palette.LABEL_WARM, false);
        }
    }

    private void drawScrollbar(GuiGraphics g, int index) {
        int max = maxScroll(index);
        if (max <= 0) return;
        Controls.Rect page = page(index);
        int top = clipTop(index), height = clipBottom(index) - top, x = page.right() - 8;
        int thumb = Math.max(10, height * height / (height + max));
        int thumbY = top + (height - thumb) * scroll[index] / max;
        g.fill(x, top, x + 2, top + height, BookRenderer.RULE);
        g.fill(x, thumbY, x + 2, thumbY + thumb, BookRenderer.LEADER);
    }

    private void drawTab(GuiGraphics g, int index) {
        PageZone zone = tabs.get(index);
        BookRenderer.tab(g, font, new Controls.Rect(zone.getX(), zone.getY(), zone.getWidth(), zone.getHeight()),
                leftPage.y() + 1, tabLabels.get(index), index == tab, zone.active && zone.hot());
    }

    // ── Input ──

    @Override public boolean mouseClicked(double mx, double my, int button) {
        mx *= shrink;
        my *= shrink;
        if (menu != null) {
            if (!menu.click(mx, my)) menu = null;
            return true;
        }
        return clicked(mx, my, button) || super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        return super.mouseReleased(mx * shrink, my * shrink, button);
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return super.mouseDragged(mx * shrink, my * shrink, button, dx * shrink, dy * shrink);
    }

    @Override public void mouseMoved(double mx, double my) {
        super.mouseMoved(mx * shrink, my * shrink);
    }

    //? if >=1.21 {
    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return wheel(mx * shrink, my * shrink, dy) || super.mouseScrolled(mx * shrink, my * shrink, dx, dy);
    }
    //?} else {
    /*@Override public boolean mouseScrolled(double mx, double my, double delta) {
        return wheel(mx * shrink, my * shrink, delta) || super.mouseScrolled(mx * shrink, my * shrink, delta);
    }
    *///?}

    private boolean wheel(double mx, double my, double delta) {
        if (menu != null && menu.contains(mx, my)) { menu.scroll(delta); return true; }
        if (scrolled(mx, my, delta)) return true;
        if (!pagesScroll() || menu != null) return false;
        for (int i = 0; i < 2; i++) {
            Controls.Rect page = page(i);
            if (mx < page.x() || mx >= page.right() || my < book.y() || my >= book.bottom()) continue;
            int next = Math.max(0, Math.min(maxScroll(i), scroll[i] - (int) Math.signum(delta) * SCROLL_STEP));
            if (next == scroll[i]) return maxScroll(i) > 0;
            scroll[i] = next;
            rebuildWidgets();
            return true;
        }
        return false;
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
