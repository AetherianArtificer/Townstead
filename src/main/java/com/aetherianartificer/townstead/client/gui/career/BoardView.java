package com.aetherianartificer.townstead.client.gui.career;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * The viewport over the career board: where the page sits under the window, how far it may be
 * dragged, and the scrollbars that admit it continues.
 *
 * <p>A page has a size. It does not resize itself to fit the desk it is lying on, and neither does
 * this board: it is drawn at reading scale and you move around it. Fitting the whole board into the
 * viewport let the amount of content decide legibility, and Minecraft's font cannot scale, so every
 * step down collided the labels. {@code zoom} stays in the arithmetic at a fixed 1 because a survey
 * mode (one deliberate step out, marks only) is the natural next addition and wants this plumbing.</p>
 *
 * <p>Takes content bounds as a parameter rather than reading the layout itself, so it is a pure
 * transform and can be reused by any board-like screen.</p>
 */
final class BoardView {

    /** Breathing room kept around the page so marks never touch the board's frame. */
    static final int MARGIN_X = 18;
    static final int MARGIN_Y = 14;

    private static final float READING_SCALE = 1f;

    private int viewX;
    private int viewY;
    private int viewW;
    private int viewH;

    private double panX;
    private double panY;
    private float zoom = READING_SCALE;
    private boolean userFramed;
    private boolean dragging;

    void setViewport(int x, int y, int w, int h) {
        viewX = x;
        viewY = y;
        viewW = w;
        viewH = h;
    }

    float zoom() { return zoom; }
    boolean userFramed() { return userFramed; }
    void setUserFramed(boolean value) { userFramed = value; }
    boolean dragging() { return dragging; }
    void setDragging(boolean value) { dragging = value; }

    int viewX() { return viewX; }
    int viewY() { return viewY; }
    int viewW() { return viewW; }
    int viewH() { return viewH; }

    private int centerX() { return viewX + viewW / 2; }
    private int originY() { return viewY + viewH / 2; }

    int screenX(int[] local) { return centerX() + Math.round(local[0] * zoom) + (int) panX; }
    int screenY(int[] local) { return originY() + Math.round(local[1] * zoom) + (int) panY; }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= viewX && mouseX < viewX + viewW
                && mouseY >= viewY && mouseY < viewY + viewH;
    }

    /**
     * Opens at the TOP of the content, centred horizontally.
     *
     * <p>Centring vertically as well put a fresh career halfway down its own tree, so switching to
     * one dropped you into the middle of a rank you had never reached. A career is read from rank
     * one downward, so that is where it opens.</p>
     */
    void frameTo(int[] bounds) {
        zoom = READING_SCALE;
        if (bounds == null || viewW <= 0 || viewH <= 0) return;
        panX = -(bounds[0] + bounds[2]) / 2f * zoom;
        panY = viewY + MARGIN_Y - (originY() + bounds[1] * zoom);
        clamp(bounds);
    }

    /** Brings a board-space point to the middle of the viewport, then clamps. */
    void centreOn(int[] local, int[] bounds) {
        if (viewW <= 0 || viewH <= 0) return;
        panX = -local[0] * zoom;
        panY = -local[1] * zoom;
        userFramed = true;
        clamp(bounds);
    }

    void panBy(double dx, double dy, int[] bounds) {
        panX += dx;
        panY += dy;
        userFramed = true;
        clamp(bounds);
    }

    /**
     * Keeps the page over the board. Content smaller than the viewport is pinned centred so it
     * cannot be dragged off into empty paper; content larger scrolls and stops when its far edge
     * reaches the far edge of the board rather than sailing past it.
     */
    void clamp(int[] bounds) {
        if (bounds == null || viewW <= 0 || viewH <= 0) return;
        panX = clampAxis(panX, bounds[0], bounds[2], centerX(), viewX, viewW, MARGIN_X);
        panY = clampAxis(panY, bounds[1], bounds[3], originY(), viewY, viewH, MARGIN_Y);
    }

    private double clampAxis(double pan, int min, int max, int origin,
                             int viewStart, int viewSize, int margin) {
        double spanStart = origin + min * zoom;
        double spanSize = (max - min) * zoom;
        double lowest = viewStart + viewSize - margin - spanStart - spanSize;
        double highest = viewStart + margin - spanStart;
        // Fits: centre it and refuse to be dragged. Overflows: scroll between the two edges.
        return lowest > highest ? (lowest + highest) / 2.0 : Mth.clamp(pan, lowest, highest);
    }

    /** True when the page is wider or taller than the board, so a scrollbar has something to say. */
    boolean overflows(int[] bounds, boolean vertical) {
        if (bounds == null) return false;
        return vertical
                ? (bounds[3] - bounds[1]) * zoom + 2 * MARGIN_Y > viewH
                : (bounds[2] - bounds[0]) * zoom + 2 * MARGIN_X > viewW;
    }

    /**
     * How far along the page you are, and how much is left. Drawn only on the axis that actually
     * overflows, because a scrollbar reporting "all of it, always" is furniture with nothing to say.
     */
    void drawScrollbars(GuiGraphics g, int[] bounds) {
        if (bounds == null) return;
        if (overflows(bounds, true)) {
            int track = viewH - MARGIN_Y;
            int span = Math.round((bounds[3] - bounds[1]) * zoom) + 2 * MARGIN_Y;
            int thumb = Math.max(12, Math.round(track * (viewH / (float) span)));
            double travel = Math.max(1, span - viewH);
            double at = (viewY + MARGIN_Y) - (originY() + bounds[1] * zoom + panY);
            int y = viewY + MARGIN_Y / 2
                    + (int) Math.round((track - thumb) * Mth.clamp(at / travel, 0, 1));
            int x = viewX + viewW - 4;
            g.fill(x, viewY + MARGIN_Y / 2, x + 2, viewY + MARGIN_Y / 2 + track, 0x2B3A2A16);
            g.fill(x, y, x + 2, y + thumb, 0x99503A18);
        }
        if (overflows(bounds, false)) {
            int track = viewW - MARGIN_X;
            int span = Math.round((bounds[2] - bounds[0]) * zoom) + 2 * MARGIN_X;
            int thumb = Math.max(12, Math.round(track * (viewW / (float) span)));
            double travel = Math.max(1, span - viewW);
            double at = (viewX + MARGIN_X) - (centerX() + bounds[0] * zoom + panX);
            int x = viewX + MARGIN_X / 2
                    + (int) Math.round((track - thumb) * Mth.clamp(at / travel, 0, 1));
            int y = viewY + viewH - 4;
            g.fill(viewX + MARGIN_X / 2, y, viewX + MARGIN_X / 2 + track, y + 2, 0x2B3A2A16);
            g.fill(x, y, x + thumb, y + 2, 0x99503A18);
        }
    }
}
