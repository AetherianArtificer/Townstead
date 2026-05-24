package com.aetherianartificer.townstead.client.gui.origin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A bordered, vanilla-style scrollable box ({@link OriginPanel} backdrop + scissor
 * clip + mouse-wheel scroll + scrollbar). Subclasses draw their content in
 * {@link #renderContent} (from a scroll-adjusted top, returning the content height)
 * and may draw a post-scissor overlay (e.g. tooltips) in {@link #renderOverlay}.
 */
abstract class ScrollPane extends AbstractWidget {

    protected static final int PAD = 4;
    private int scrollOffset;
    private int contentHeight;

    ScrollPane(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    protected void resetScroll() {
        scrollOffset = 0;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        OriginPanel.draw(g, x, y, x + w, y + h);
        int left = x + PAD;
        int innerTop = y + PAD;
        int innerBottom = y + h - PAD;
        int innerW = w - PAD * 2 - 4; // room for the scrollbar

        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        contentHeight = renderContent(g, left, innerTop - scrollOffset, innerW, innerTop, innerBottom, mouseX, mouseY);
        g.disableScissor();

        int innerH = innerBottom - innerTop;
        int maxScroll = Math.max(0, contentHeight - innerH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (maxScroll > 0) {
            int trackX = x + w - 3;
            int thumbH = Math.max(12, innerH * innerH / contentHeight);
            int thumbY = innerTop + (innerH - thumbH) * scrollOffset / maxScroll;
            g.fill(trackX, innerTop, trackX + 2, innerBottom, 0x50000000);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFFAAAAAA);
        }
        renderOverlay(g, mouseX, mouseY, innerTop, innerBottom);
    }

    /** Draw content from {@code top} (already scroll-adjusted); return total content height (px). */
    protected abstract int renderContent(GuiGraphics g, int left, int top, int innerW,
                                         int innerTop, int innerBottom, int mouseX, int mouseY);

    /** Drawn after the scissor is released (for tooltips). Default no-op. */
    protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, int innerTop, int innerBottom) {}

    //? if >=1.21 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollBy(mouseX, mouseY, scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return scrollBy(mouseX, mouseY, delta);
    }
    *///?}

    private boolean scrollBy(double mouseX, double mouseY, double dy) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        int innerH = getHeight() - PAD * 2;
        int maxScroll = Math.max(0, contentHeight - innerH);
        if (maxScroll <= 0) return false;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (dy * 12)));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
