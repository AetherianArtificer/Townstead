package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * A text field made of paper rather than of vanilla's black box.
 *
 * <p>An unstyled {@code EditBox} on a Townstead screen is the same mistake the vanilla button was
 * on the Career record: a black rectangle is the one thing in the frame that plainly belongs to a
 * different game. This writes on a pale slip instead, so a search field reads as a line ruled on
 * the page.</p>
 */
public class PaperField extends EditBox {

    public PaperField(Font font, int x, int y, int w, int h, Component placeholder) {
        super(font, x, y, w, h, placeholder);
        setBordered(false);
        setTextColor(Palette.CARD_INK);
        setTextColorUneditable(Palette.CARD_INK_DIM);
        setHint(placeholder);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX() - 3;
        int y = getY() - 3;
        int w = getWidth() + 6;
        int h = getHeight() + 6;

        g.fill(x, y, x + w, y + h, Palette.CARD);
        // Focus is shown by warming the edge, not by an outline appearing from nowhere.
        Palette.drawOutline(g, x, y, x + w, y + h,
                isFocused() ? Palette.BAR_FILL : Palette.DESK_LIP);
        super.renderWidget(g, mouseX, mouseY, partialTick);
    }
}
