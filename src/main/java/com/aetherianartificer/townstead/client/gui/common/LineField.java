package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Writing on a line of the page. No box: the text is ink, the line under it is dotted until the
 * pointer finds it and solid brass while written on. The faction's name is written at twice the
 * size, so the field draws its own text at an integer scale and maps clicks back to characters.
 */
public class LineField extends EditBox {
    private final Font font;
    private final Component hint;
    private final int scale;
    private final boolean centred;
    private int mark;
    private int ink = BookRenderer.INK;

    /** {@code scale} is 1 or 2. A value too wide for the line at 2 is written at 1. */
    public LineField(Font font, int x, int y, int w, Component hint, int scale, boolean centred) {
        super(font, x, y, w, height(scale), hint);
        this.font = font;
        this.hint = hint;
        this.scale = Math.max(1, scale);
        this.centred = centred;
        setBordered(false);
    }

    /** The field's height: one line of text at the scale, a pixel of air, and the line itself. */
    public static int height(int scale) {
        return 9 * Math.max(1, scale) + 2;
    }

    public LineField ink(int color) {
        this.ink = color;
        return this;
    }

    private int shownScale() {
        return font.width(getValue()) * scale <= getWidth() ? scale : 1;
    }

    private int textX(int s) {
        int tw = font.width(getValue()) * s;
        return centred ? getX() + (getWidth() - Math.min(tw, getWidth())) / 2 : getX();
    }

    private int textY(int s) {
        return getY() + (9 * scale - 9 * s);
    }

    @Override
    public void setHighlightPos(int pos) {
        super.setHighlightPos(pos);
        mark = Math.max(0, Math.min(pos, getValue().length()));
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        int s = shownScale();
        String value = getValue();
        int x = textX(s), y = textY(s);
        boolean focused = isFocused();
        g.enableScissor(getX() - 1, getY() - 1, getX() + getWidth() + 1, getY() + getHeight() + 1);
        if (value.isEmpty() && !focused) {
            drawScaled(g, hint.getString(), centred ? getX() + (getWidth() - font.width(hint) * s) / 2 : getX(), y, s, BookRenderer.FADED);
        } else {
            int cursor = Math.min(getCursorPosition(), value.length());
            int from = Math.min(cursor, mark), to = Math.max(cursor, mark);
            if (focused && from != to) {
                int sx = x + font.width(value.substring(0, from)) * s;
                int ex = x + font.width(value.substring(0, to)) * s;
                g.fill(sx, y - 1, ex, y + 9 * s, 0x558A5F1E);
            }
            drawScaled(g, value, x, y, s, ink);
            if (focused && (Util.getMillis() / 500L) % 2L == 0L) {
                int cx = x + font.width(value.substring(0, cursor)) * s;
                g.fill(cx, y - 1, cx + 1, y + 8 * s, BookRenderer.INK);
            }
        }
        g.disableScissor();
        BookRenderer.line(g, getX(), getY() + getHeight() - 1, getWidth(), isHovered(), focused);
    }

    private void drawScaled(GuiGraphics g, String text, int x, int y, int s, int color) {
        if (s == 1) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(s, s, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled && isFocused() && isMouseOver(mouseX, mouseY)) {
            int index = indexAt(mouseX);
            setCursorPosition(index);
            setHighlightPos(index);
        }
        return handled;
    }

    /** The character boundary nearest the pointer, in the scaled layout this field draws. */
    private int indexAt(double mouseX) {
        String value = getValue();
        int s = shownScale();
        double local = (mouseX - textX(s)) / s;
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i <= value.length(); i++) {
            double distance = Math.abs(font.width(value.substring(0, i)) - local);
            if (distance < bestDistance) { bestDistance = distance; best = i; }
        }
        return best;
    }
}
