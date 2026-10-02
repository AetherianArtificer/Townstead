package com.aetherianartificer.townstead.client.gui.switchboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A Game Rules style list: yellow group headings, then a label on the left and a control on the right. */
public class SettingList extends ContainerObjectSelectionList<SettingList.Row> {
    private final int rowWidth;

    public SettingList(Minecraft mc, int width, int height, int y) {
        this(mc, width, height, y, 310);
    }

    //? if >=1.21 {
    public SettingList(Minecraft mc, int width, int height, int y, int rowWidth) {
        super(mc, width, height, y, 24);
        this.rowWidth = rowWidth;
    }
    //?} else {
    /*public SettingList(Minecraft mc, int width, int height, int y, int rowWidth) {
        super(mc, width, height, y, y + height, 24);
        this.rowWidth = rowWidth;
    }
    *///?}

    public void add(Row row) {
        addEntry(row);
    }

    public void clear() {
        clearEntries();
    }

    public boolean isEmpty() {
        return children().isEmpty();
    }

    @Override
    public int getRowWidth() {
        return rowWidth;
    }

    @Override
    protected int getScrollbarPosition() {
        return getRowLeft() + rowWidth + 6;
    }

    public abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {}

    public static final class Header extends Row {
        private final Component label;

        public Header(Component label) {
            this.label = label;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = Minecraft.getInstance().font;
            g.drawCenteredString(font, label, left + width / 2, top + 8, 0xFFFFFF);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(new NarratableEntry() {
                @Override public NarrationPriority narrationPriority() { return NarrationPriority.HOVERED; }
                @Override public void updateNarration(NarrationElementOutput output) {
                    output.add(NarratedElementType.TITLE, label);
                }
            });
        }
    }

    /** Column headings over the controls of the rows below, right-aligned the way {@link Setting} lays them out. */
    public static final class Columns extends Row {
        public record Column(Component label, int width, @Nullable Component tooltip) {}

        private final net.minecraft.client.gui.screens.Screen screen;
        private final List<Column> columns;

        public Columns(net.minecraft.client.gui.screens.Screen screen, List<Column> columns) {
            this.screen = screen;
            this.columns = List.copyOf(columns);
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int x = left + width;
            for (int i = columns.size() - 1; i >= 0; i--) {
                Column column = columns.get(i);
                x -= column.width();
                Component label = column.label().copy().withStyle(net.minecraft.ChatFormatting.GRAY,
                        net.minecraft.ChatFormatting.UNDERLINE);
                g.drawCenteredString(font, label, x + column.width() / 2, top + 12, 0xFFFFFF);
                if (column.tooltip() != null && mouseX >= x && mouseX < x + column.width()
                        && mouseY >= top && mouseY < top + height) {
                    screen.setTooltipForNextRenderPass(font.split(column.tooltip(), 220));
                }
                x -= Setting.GAP;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /** One line of Review Changes: the setting, its old value struck through, and the new value. */
    public static final class Change extends Row {
        private final Component label;
        private final Component from;
        private final Component to;

        public Change(Component label, Component from, Component to) {
            this.label = label;
            this.from = from.copy().withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.STRIKETHROUGH);
            this.to = to.copy().withStyle(net.minecraft.ChatFormatting.YELLOW);
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = Minecraft.getInstance().font;
            Component values = Component.empty().append(from).append(Component.literal("  >  ")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)).append(to);
            int valuesWidth = Math.min(font.width(values), width / 2);
            int right = left + width;
            List<FormattedCharSequence> name = font.split(label, width - valuesWidth - 8);
            if (!name.isEmpty()) g.drawString(font, name.get(0), left, top + 6, 0xFFFFFF, false);
            List<FormattedCharSequence> shown = font.split(values, width / 2);
            if (!shown.isEmpty()) g.drawString(font, shown.get(0), right - font.width(shown.get(0)), top + 6, 0xFFFFFF, false);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(new NarratableEntry() {
                @Override public NarrationPriority narrationPriority() { return NarrationPriority.HOVERED; }
                @Override public void updateNarration(NarrationElementOutput output) {
                    output.add(NarratedElementType.TITLE, Component.empty().append(label).append(": ")
                            .append(from).append(" > ").append(to));
                }
            });
        }
    }

    /** A label on the left and one or more controls on the right, laid out right to left. */
    public static final class Setting extends Row {
        private static final int GAP = 4;
        private final net.minecraft.client.gui.screens.Screen screen;
        private final Component label;
        @Nullable private final Component tooltip;
        private final List<AbstractWidget> controls;
        private final boolean dimmed;

        public Setting(net.minecraft.client.gui.screens.Screen screen, Component label, @Nullable Component tooltip,
                       AbstractWidget control, int controlWidth) {
            this(screen, label, tooltip, List.of(control), !control.active);
        }

        public Setting(net.minecraft.client.gui.screens.Screen screen, Component label, @Nullable Component tooltip,
                       List<AbstractWidget> controls, boolean dimmed) {
            this.screen = screen;
            this.label = label;
            this.tooltip = tooltip;
            this.controls = List.copyOf(controls);
            this.dimmed = dimmed;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int x = left + width;
            for (int i = controls.size() - 1; i >= 0; i--) {
                AbstractWidget control = controls.get(i);
                x -= control.getWidth();
                control.setX(x);
                control.setY(top);
                control.render(g, mouseX, mouseY, partialTick);
                x -= GAP;
            }
            int labelWidth = x - left - GAP;
            List<FormattedCharSequence> lines = font.split(label, Math.max(20, labelWidth));
            int color = dimmed ? 0xA0A0A0 : 0xFFFFFF;
            if (lines.size() == 1) {
                g.drawString(font, lines.get(0), left, top + 6, color, false);
            } else if (!lines.isEmpty()) {
                g.drawString(font, lines.get(0), left, top + 1, color, false);
                g.drawString(font, lines.get(1), left, top + 11, color, false);
            }
            if (tooltip != null && hovered && mouseX < x) {
                screen.setTooltipForNextRenderPass(font.split(tooltip, 220));
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return controls;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return controls;
        }
    }
}
