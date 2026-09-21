package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;

/** Registers a virtual-coordinate native widget with a screen using physical GUI coordinates. */
public final class ScaledWidget extends AbstractWidget {
    private final AbstractWidget delegate;
    private final float scale;

    public ScaledWidget(AbstractWidget delegate, float scale) {
        super(Math.round(delegate.getX() * scale), Math.round(delegate.getY() * scale),
                Math.round(delegate.getWidth() * scale), Math.round(delegate.getHeight() * scale), delegate.getMessage());
        this.delegate = delegate;
        this.scale = scale;
        sync();
    }
    public void sync() { visible = delegate.visible; active = delegate.active; }
    @Override public boolean isMouseOver(double x, double y) { return delegate.visible && delegate.isMouseOver(x / scale, y / scale); }
    @Override public boolean mouseClicked(double x, double y, int button) { return delegate.mouseClicked(x / scale, y / scale, button); }
    @Override public boolean mouseReleased(double x, double y, int button) { return delegate.mouseReleased(x / scale, y / scale, button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return delegate.mouseDragged(x / scale, y / scale, button, dx / scale, dy / scale);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) { return delegate.keyPressed(key, scan, modifiers); }
    @Override public boolean keyReleased(int key, int scan, int modifiers) { return delegate.keyReleased(key, scan, modifiers); }
    @Override public boolean charTyped(char chr, int modifiers) { return delegate.charTyped(chr, modifiers); }
    @Override public void setFocused(boolean focused) { super.setFocused(focused); delegate.setFocused(focused); }
    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) { /* Owner renders the shared transform. */ }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { delegate.updateNarration(output); }

    public static void scissor(GuiGraphics g, float scale, int x, int y, int right, int bottom) {
        g.enableScissor((int) Math.floor(x * scale), (int) Math.floor(y * scale),
                (int) Math.ceil(right * scale), (int) Math.ceil(bottom * scale));
    }
}
