package com.aetherianartificer.townstead.client.gui.character;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** One exact authored hair colour as a clickable square, outlined while it is the current dye. */
public class HairSwatchWidget extends AbstractWidget {
    private final int argb;
    private final Runnable onPick;
    private boolean current;

    public HairSwatchWidget(int x, int y, int size, int argb, Runnable onPick) {
        super(x, y, size, size, Component.literal(""));
        this.argb = argb;
        this.onPick = onPick;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
        context.fill(x0, y0, x1, y1, current ? 0xffffffff : 0xaaffffff);
        context.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xff000000 | argb);
        if (current) {
            // Inner dark ring so the highlight reads on pale colours too.
            context.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, 0x80000000);
            context.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, 0x80000000);
            context.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, 0x80000000);
            context.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, 0x80000000);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(mouseX, mouseY)) return false;
        onPick.run();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        defaultButtonNarrationText(builder);
    }
}
