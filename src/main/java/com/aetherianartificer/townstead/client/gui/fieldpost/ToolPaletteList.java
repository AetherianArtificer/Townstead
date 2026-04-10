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
    private Consumer<String> onHeaderClick;
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

    public void setOnHeaderClick(Consumer<String> callback) {
        this.onHeaderClick = callback;
    }

    public void replaceEntries(List<ToolEntry> entries) {
        clearEntries();
        for (ToolEntry entry : entries) {
            entry.parentList = this;
            addEntry(entry);
        }
    }

    @Override
    public void setSelected(ToolEntry entry) {
        // Don't allow headers to be "selected" — they just toggle
        if (entry != null && entry.isHeader) return;
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
        public final boolean isHeader;
        public final String categoryKey;  // for headers: the category; for tools: the category they belong to
        public final int memberCount;     // only meaningful for headers
        public ToolPaletteList parentList; // set when added, used for header-click routing

        /** Constructor for tool entries. */
        public ToolEntry(String toolId, String label, ItemStack icon, String categoryKey) {
            this.toolId = toolId;
            this.label = label;
            this.icon = icon;
            this.isHeader = false;
            this.categoryKey = categoryKey;
            this.memberCount = 0;
        }

        /** Constructor for category headers. */
        public static ToolEntry header(String categoryKey, int memberCount) {
            return new ToolEntry(categoryKey, memberCount);
        }

        private ToolEntry(String categoryKey, int memberCount) {
            this.toolId = "__header__" + categoryKey;
            this.label = categoryKey;
            this.icon = ItemStack.EMPTY;
            this.isHeader = true;
            this.categoryKey = categoryKey;
            this.memberCount = memberCount;
        }

        private boolean isCollapsed() {
            if (parentList == null) return false;
            return parentList.isCategoryCollapsed(categoryKey);
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            Minecraft mc = Minecraft.getInstance();

            if (isHeader) {
                renderHeader(g, mc, top, left, width, height, hovered);
            } else {
                renderTool(g, mc, top, left, width, height, hovered);
            }
        }

        private void renderHeader(GuiGraphics g, Minecraft mc, int top, int left, int width, int height, boolean hovered) {
            // Header background strip
            g.fill(left - 1, top, left + width - 1, top + height - 2,
                    hovered ? 0xFF2A2518 : 0xFF1A1610);
            // Bottom accent line
            g.fill(left - 1, top + height - 3, left + width - 1, top + height - 2, 0x40FFDEA0);

            // Expand/collapse arrow
            String arrow = isCollapsed() ? "\u25B6" : "\u25BC";
            g.drawString(mc.font, arrow, left + 3, top + 7, 0xFFFFDEA0, false);

            // Category label
            String title = label.toUpperCase();
            g.drawString(mc.font, title, left + 14, top + 7, hovered ? 0xFFFFDEA0 : 0xFFDDCC99, false);

            // Member count on the right
            String count = String.valueOf(memberCount);
            int countW = mc.font.width(count);
            g.drawString(mc.font, count, left + width - countW - 8, top + 7, 0xFF888066, false);
        }

        private void renderTool(GuiGraphics g, Minecraft mc, int top, int left, int width, int height, boolean hovered) {
            // Indent tools slightly so categories stand out
            int indent = 6;

            // Icon
            g.renderItem(icon, left + indent, top + 2);

            // Label
            int textColor = hovered ? 0xFFFFFFFF : 0xFFCCCCCC;
            String displayLabel = label;
            int maxW = width - indent - 24;
            if (mc.font.width(displayLabel) > maxW) {
                while (mc.font.width(displayLabel + "..") > maxW && displayLabel.length() > 1)
                    displayLabel = displayLabel.substring(0, displayLabel.length() - 1);
                displayLabel += "..";
            }
            g.drawString(mc.font, displayLabel, left + indent + 20, top + 6, textColor, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isHeader && button == 0 && parentList != null && parentList.onHeaderClick != null) {
                parentList.onHeaderClick.accept(categoryKey);
                return true;
            }
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(label);
        }
    }

    public boolean isCategoryCollapsed(String key) {
        return collapsedCategories.contains(key);
    }

    private final java.util.Set<String> collapsedCategories = new java.util.HashSet<>();

    public void toggleCategory(String key) {
        if (collapsedCategories.contains(key)) collapsedCategories.remove(key);
        else collapsedCategories.add(key);
    }

    /**
     * Override addEntry so we can set parentList on entries.
     */
    public void addEntryWithParent(ToolEntry entry) {
        entry.parentList = this;
        addEntry(entry);
    }
}
