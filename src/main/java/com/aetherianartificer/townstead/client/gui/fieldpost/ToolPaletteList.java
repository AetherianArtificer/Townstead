package com.aetherianartificer.townstead.client.gui.fieldpost;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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
        return this.width - 16;
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

    // Suppress the default dirt/menu background so our transparent panel shows through
    @Override
    protected void renderListBackground(GuiGraphics g) {}

    // Suppress the default list separators (horizontal bars at top/bottom)
    @Override
    protected void renderListSeparators(GuiGraphics g) {}
    //?}

    public static class ToolEntry extends ObjectSelectionList.Entry<ToolEntry> {
        public final String toolId;
        public final String label;
        public final ItemStack icon;
        @Nullable public final ResourceLocation customIcon; // custom texture instead of item icon
        public final boolean isHeader;
        public final String categoryKey;
        public final int memberCount;
        public ToolPaletteList parentList;

        /** Constructor for tool entries with an item icon. */
        public ToolEntry(String toolId, String label, ItemStack icon, String categoryKey) {
            this.toolId = toolId;
            this.label = label;
            this.icon = icon;
            this.customIcon = null;
            this.isHeader = false;
            this.categoryKey = categoryKey;
            this.memberCount = 0;
        }

        /** Constructor for tool entries with a custom texture icon. */
        public ToolEntry(String toolId, String label, ResourceLocation customIcon, String categoryKey) {
            this.toolId = toolId;
            this.label = label;
            this.icon = ItemStack.EMPTY;
            this.customIcon = customIcon;
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
            this.customIcon = null;
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
            // Stretch to full list width (ignore the row inset)
            int fullLeft = parentList != null ? parentList.xPos : left;
            int fullWidth = parentList != null ? parentList.width - 6 : width; // leave room for scrollbar
            int btnTop = top - 1;
            int btnBottom = top + height + 1;
            int btnRight = fullLeft + fullWidth;

            // Base fill (gray)
            int bodyColor = hovered ? 0xFF6F6F6F : 0xFF545454;
            g.fill(fullLeft + 1, btnTop + 1, btnRight - 1, btnBottom - 1, bodyColor);

            // Top highlight strip (lighter)
            g.fill(fullLeft + 1, btnTop + 1, btnRight - 1, btnTop + 2,
                    hovered ? 0xFF909090 : 0xFF737373);
            // Bottom shadow strip (darker)
            g.fill(fullLeft + 1, btnBottom - 2, btnRight - 1, btnBottom - 1, 0xFF3A3A3A);

            // Dark outer border (1px all around)
            g.fill(fullLeft, btnTop, btnRight, btnTop + 1, 0xFF000000);
            g.fill(fullLeft, btnBottom - 1, btnRight, btnBottom, 0xFF000000);
            g.fill(fullLeft, btnTop, fullLeft + 1, btnBottom, 0xFF000000);
            g.fill(btnRight - 1, btnTop, btnRight, btnBottom, 0xFF000000);

            // Arrow and label — symmetric padding from edges
            int pad = 5;
            int textY = btnTop + (btnBottom - btnTop - 8) / 2 + 1;
            String arrow = isCollapsed() ? "\u25B6" : "\u25BC";
            g.drawString(mc.font, arrow, fullLeft + pad, textY, 0xFFFFFFFF, true);
            g.drawString(mc.font, label, fullLeft + pad + 10, textY, hovered ? 0xFFFFFFA0 : 0xFFFFFFFF, true);

            // Member count — same padding from the right as arrow from the left
            String count = "(" + memberCount + ")";
            int countW = mc.font.width(count);
            g.drawString(mc.font, count, btnRight - countW - pad, textY, 0xFFA0A0A0, true);
        }

        private void renderTool(GuiGraphics g, Minecraft mc, int top, int left, int width, int height, boolean hovered) {
            int indent = 2;

            // Icon — custom texture or item stack
            if (customIcon != null) {
                //? if >=1.21 {
                g.blit(customIcon, left + indent, top + 3, 0, 0, 16, 16, 16, 16);
                //?} else {
                /*com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, customIcon);
                g.blit(customIcon, left + indent, top + 3, 0, 0, 16, 16, 16, 16);
                *///?}
            } else {
                g.renderItem(icon, left + indent, top + 2);
            }

            // Label + assignment count
            int textColor = hovered ? 0xFFFFFFFF : 0xFFCCCCCC;
            int count = parentList != null ? parentList.getAssignmentCount(toolId) : 0;
            String countStr = count > 0 ? " (" + count + ")" : "";
            int countW = count > 0 ? mc.font.width(countStr) : 0;
            String displayLabel = label;
            int maxW = width - indent - 24 - countW;
            if (mc.font.width(displayLabel) > maxW) {
                while (mc.font.width(displayLabel + "..") > maxW && displayLabel.length() > 1)
                    displayLabel = displayLabel.substring(0, displayLabel.length() - 1);
                displayLabel += "..";
            }
            int labelX = left + indent + 20;
            g.drawString(mc.font, displayLabel, labelX, top + 6, textColor, false);
            if (count > 0) {
                g.drawString(mc.font, countStr, labelX + mc.font.width(displayLabel), top + 6, 0xFF88AA66, false);
            }
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
    private java.util.Map<String, Integer> assignmentCounts = java.util.Map.of();

    /** Updates the count of how many cells are assigned to each tool ID. */
    public void setAssignmentCounts(java.util.Map<String, Integer> counts) {
        this.assignmentCounts = counts != null ? counts : java.util.Map.of();
    }

    public int getAssignmentCount(String toolId) {
        return assignmentCounts.getOrDefault(toolId, 0);
    }

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
