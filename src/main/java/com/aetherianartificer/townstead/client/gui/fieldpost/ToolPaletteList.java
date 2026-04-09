package com.aetherianartificer.townstead.client.gui.fieldpost;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

public class ToolPaletteList extends ObjectSelectionList<ToolPaletteList.ToolEntry> {

    private static final int ITEM_HEIGHT = 22;
    private final Consumer<ToolEntry> onSelectionChanged;
    private final int xPos;

    //? if >=1.21 {
    public ToolPaletteList(Minecraft mc, int x, int width, int height, int y, Consumer<ToolEntry> onSelectionChanged) {
        super(mc, width, height, y, ITEM_HEIGHT);
        this.onSelectionChanged = onSelectionChanged;
        this.xPos = x;
        this.setX(x);
    }
    //?} else {
    /*public ToolPaletteList(Minecraft mc, int x, int width, int height, int top, Consumer<ToolEntry> onSelectionChanged) {
        super(mc, width, height, top, top + height, ITEM_HEIGHT);
        this.onSelectionChanged = onSelectionChanged;
        this.xPos = x;
        this.x0 = x;
        this.x1 = x + width;
        setRenderBackground(false);
    }
    *///?}

    public void replaceEntries(List<ToolEntry> entries) {
        clearEntries();
        for (ToolEntry entry : entries) {
            addEntry(entry);
        }
    }

    @Override
    public void setSelected(ToolEntry entry) {
        super.setSelected(entry);
        if (onSelectionChanged != null) onSelectionChanged.accept(entry);
    }

    @Override
    public int getRowWidth() {
        return this.width - 12;
    }

    @Override
    protected int getScrollbarPosition() {
        return xPos + this.width - 6;
    }

    //? if >=1.21 {
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.getX() && mouseX < this.getX() + this.width
                && mouseY >= this.getY() && mouseY < this.getY() + this.getHeight();
    }
    //?}

    public static class ToolEntry extends ObjectSelectionList.Entry<ToolEntry> {
        public final String toolId;
        public final String label;
        public final ItemStack icon;

        public ToolEntry(String toolId, String label, ItemStack icon) {
            this.toolId = toolId;
            this.label = label;
            this.icon = icon;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            Minecraft mc = Minecraft.getInstance();

            // Icon
            g.renderItem(icon, left + 2, top + 2);

            // Label
            int textColor = hovered ? 0xFFFFFFFF : 0xFFCCCCCC;
            String displayLabel = label;
            int maxW = width - 24;
            if (mc.font.width(displayLabel) > maxW) {
                while (mc.font.width(displayLabel + "..") > maxW && displayLabel.length() > 1)
                    displayLabel = displayLabel.substring(0, displayLabel.length() - 1);
                displayLabel += "..";
            }
            g.drawString(mc.font, displayLabel, left + 22, top + 6, textColor, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(label);
        }
    }
}
