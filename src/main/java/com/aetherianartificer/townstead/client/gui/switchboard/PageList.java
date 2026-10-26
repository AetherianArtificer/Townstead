package com.aetherianartificer.townstead.client.gui.switchboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A tab's full-width controls, each with an optional line under it, scrolling over the screen's own background. */
final class PageList extends ContainerObjectSelectionList<PageList.Row> {
    private static final int ROW_HEIGHT = 38;
    private final int rowWidth;

    //? if >=1.21 {
    PageList(Minecraft mc, int width, int height, int y, int rowWidth) {
        super(mc, width, height, y, ROW_HEIGHT);
        this.rowWidth = rowWidth;
    }

    @Override
    protected void renderListBackground(GuiGraphics g) {}

    @Override
    protected void renderListSeparators(GuiGraphics g) {}
    //?} else {
    /*PageList(Minecraft mc, int width, int height, int y, int rowWidth) {
        super(mc, width, height, y, y + height, ROW_HEIGHT);
        this.rowWidth = rowWidth;
        setRenderBackground(false);
    }

    // 1.20.1 paints dirt bands above and below the list; clip them away so the tab bar and buttons show.
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.enableScissor(this.x0, this.y0, this.x1, this.y1);
        super.render(g, mouseX, mouseY, partial);
        g.disableScissor();
    }
    *///?}

    void add(AbstractWidget control, @Nullable Component hint) {
        addEntry(new Row(control, hint));
    }

    @Override
    public int getRowWidth() {
        return rowWidth;
    }

    @Override
    protected int getScrollbarPosition() {
        return getRowLeft() + rowWidth + 6;
    }

    static final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final AbstractWidget control;
        @Nullable private final Component hint;

        Row(AbstractWidget control, @Nullable Component hint) {
            this.control = control;
            this.hint = hint;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            control.setX(left + (width - control.getWidth()) / 2);
            control.setY(top);
            control.render(g, mouseX, mouseY, partialTick);
            if (hint == null) return;
            Font font = Minecraft.getInstance().font;
            List<FormattedCharSequence> lines = font.split(hint, width);
            if (!lines.isEmpty()) g.drawCenteredString(font, lines.get(0), left + width / 2, top + 24, 0xA0A0A0);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(control);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(control);
        }
    }
}
