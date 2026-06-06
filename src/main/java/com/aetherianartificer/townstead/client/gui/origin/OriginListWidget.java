package com.aetherianartificer.townstead.client.gui.origin;

import com.aetherianartificer.townstead.client.origin.OriginCatalogClient;
import com.aetherianartificer.townstead.client.origin.OriginClientStore;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Master list of the origin catalog, grouped by species (non-interactive header
 * rows) and filterable by a search string. Selecting an origin row fires
 * {@code onSelect}. The row matching the target's current origin (live from
 * {@link OriginClientStore}, default Overworlder) is marked.
 *
 * <p>Cross-version {@code ObjectSelectionList} handling mirrors
 * {@code fieldpost/ToolPaletteList}.</p>
 */
public class OriginListWidget extends ObjectSelectionList<OriginListWidget.Row> {

    private static final int ROW_HEIGHT = 15;
    // Vanilla anchors row 0 at getY()+4, leaving a fat top gap. We shift the widget up by
    // TOP_INSET and draw the panel/scissor that much lower, so row 0 lands (4 - TOP_INSET)px
    // below the panel's top border: a selected row then has the same 1px inset from the top
    // frame as the selection fill has from the left/right edges. Clicks (getEntryAtPosition's
    // -4) and scroll range (getMaxScroll's -4) stay consistent.
    private static final int TOP_INSET = 2;
    private final int xPos;
    private final int targetEntityId;
    private Consumer<OriginCatalogEntry> onSelect;
    private String filter = "";
    private boolean shown = true;

    //? if >=1.21 {
    public OriginListWidget(Minecraft mc, int x, int width, int height, int y, int targetEntityId) {
        super(mc, width, height + TOP_INSET, y - TOP_INSET, ROW_HEIGHT);
        this.xPos = x;
        this.targetEntityId = targetEntityId;
        this.setX(x);
    }
    //?} else {
    /*public OriginListWidget(Minecraft mc, int x, int width, int height, int top, int targetEntityId) {
        super(mc, width, height + TOP_INSET, top - TOP_INSET, top + height, ROW_HEIGHT);
        this.xPos = x;
        this.targetEntityId = targetEntityId;
        this.x0 = x;
        this.x1 = x + width;
        setRenderBackground(false);
    }
    *///?}

    public void setOnSelect(Consumer<OriginCatalogEntry> onSelect) {
        this.onSelect = onSelect;
    }

    /**
     * Show or hide the list when the picker flips between its Origin and Genes
     * views. {@code ObjectSelectionList}'s class hierarchy differs across versions
     * (an {@code AbstractWidget} on 1.21, a bare container on 1.20.1), so render
     * and input are gated explicitly here rather than relying on {@code visible}.
     */
    public void setShown(boolean shown) {
        this.shown = shown;
        //? if >=1.21 {
        this.visible = shown;
        //?}
    }

    public void setFilter(String filter) {
        this.filter = filter == null ? "" : filter.toLowerCase(Locale.ROOT).trim();
        rebuild();
    }

    /** (Re)build rows from the synced catalog, grouped by species and alphabetized, applying the filter. */
    public void rebuild() {
        clearEntries();
        // TreeMap keeps the species groups in alphabetical order; each group's origins are sorted by
        // display name below. With a single species (no headers) this is just a flat A→Z origin list.
        Map<String, List<OriginCatalogEntry>> bySpecies = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (OriginCatalogEntry e : OriginCatalogClient.origins()) {
            if (!filter.isEmpty()
                    && !e.name().toLowerCase(Locale.ROOT).contains(filter)
                    && !e.demonymPlural().toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            bySpecies.computeIfAbsent(e.speciesName(), k -> new java.util.ArrayList<>()).add(e);
        }
        boolean grouped = bySpecies.size() > 1;
        for (Map.Entry<String, List<OriginCatalogEntry>> group : bySpecies.entrySet()) {
            group.getValue().sort(java.util.Comparator.comparing(OriginCatalogEntry::name, String.CASE_INSENSITIVE_ORDER));
            if (grouped && !group.getKey().isEmpty()) {
                addEntry(new Row(this, null, group.getKey()));
            }
            for (OriginCatalogEntry e : group.getValue()) {
                addEntry(new Row(this, e, null));
            }
        }
    }

    String currentOriginId() {
        String id = OriginClientStore.get(targetEntityId);
        return id.isEmpty()
                ? com.aetherianartificer.townstead.origin.OriginRegistry.DEFAULT_ID.toString()
                : id;
    }

    void choose(OriginCatalogEntry entry) {
        if (entry != null) {
            setSelected(rowFor(entry));
            if (onSelect != null) onSelect.accept(entry);
        }
    }

    private Row rowFor(OriginCatalogEntry entry) {
        for (Row r : children()) {
            if (r.entry == entry) return r;
        }
        return null;
    }

    /** The currently selected origin entry, or null. */
    public OriginCatalogEntry selectedOrigin() {
        Row r = getSelected();
        return r != null ? r.entry : null;
    }

    /** Scroll so the selected row is on screen (reveals the target's current origin on open). */
    public void scrollToSelected() {
        Row r = getSelected();
        if (r != null) ensureVisible(r);
    }

    @Override
    public int getRowWidth() {
        return this.width;
    }

    // Vanilla getRowLeft() centers and adds a hidden +2; pin to the panel's left edge
    // so a selected row's fill spans the full width edge-to-edge.
    @Override
    public int getRowLeft() {
        return this.xPos;
    }

    @Override
    protected int getScrollbarPosition() {
        return xPos + this.width - 6;
    }

    private static void drawPanel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        OriginPanel.draw(g, x0, y0, x1, y1);
    }

    //? if >=1.21 {
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return shown && mouseX >= this.getX() && mouseX < this.getX() + this.width
                && mouseY >= this.getY() + TOP_INSET && mouseY < this.getY() + this.getHeight();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return shown && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return shown && super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // Clip away the TOP_INSET strip the widget-shift exposes above the panel: vanilla draws
    // the scrollbar from getY() (outside its own scissor), so wrap the whole render in one.
    // Then stamp the frame back on top so vanilla's top edge-fade can't swallow the border.
    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int top = this.getY() + TOP_INSET;
        int bottom = this.getY() + this.getHeight();
        g.enableScissor(this.getX(), top, this.getX() + this.width, bottom);
        super.renderWidget(g, mouseX, mouseY, partialTick);
        g.disableScissor();
        OriginPanel.drawTopEdge(g, this.getX(), top, this.getX() + this.width);
    }

    @Override
    protected void renderListBackground(GuiGraphics g) {
        drawPanel(g, this.getX(), this.getY() + TOP_INSET, this.getX() + this.width, this.getY() + this.getHeight());
    }

    @Override
    protected void renderListSeparators(GuiGraphics g) {}
    //?} else {
    /*@Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (!shown) return;
        int panelTop = this.y0 + TOP_INSET;
        drawPanel(g, this.x0, panelTop, this.x1, this.y1);
        g.enableScissor(this.x0, panelTop, this.x1, this.y1);
        super.render(g, mouseX, mouseY, partial);
        g.disableScissor();
        OriginPanel.drawTopEdge(g, this.x0, panelTop, this.x1);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return shown && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return shown && super.mouseScrolled(mouseX, mouseY, delta);
    }
    *///?}

    public static class Row extends ObjectSelectionList.Entry<Row> {
        private final OriginListWidget parent;
        private final OriginCatalogEntry entry; // null for a species header
        private final String header;

        Row(OriginListWidget parent, OriginCatalogEntry entry, String header) {
            this.parent = parent;
            this.entry = entry;
            this.header = header;
        }

        boolean isHeader() {
            return entry == null;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            int textY = top + (height - 8) / 2 + 1; // vertically centered, nudged down 1px
            int textX = left + 4;                    // left-aligned, matching the description/search text
            if (isHeader()) {
                g.drawString(mc.font, header, textX, textY, 0xFFB89A6A, false);
                return;
            }
            boolean current = entry.id().equals(parent.currentOriginId());
            boolean selected = parent.getSelected() == this;
            // Fill the selection (2px inset left/right to match the top/bottom margins),
            // covering vanilla's selection outline.
            if (selected || current) {
                g.fill(left + 2, top, left + width - 2, top + height, 0xFF2F6B2F);
            } else if (hovered) {
                g.fill(left + 2, top, left + width - 2, top + height, 0x40FFFFFF);
            }
            int color = selected || current || hovered ? 0xFFFFFFFF : 0xFFD8D8D8;
            g.drawString(mc.font, entry.name(), textX, textY, color, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isHeader() || button != 0) return false;
            parent.choose(entry);
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(isHeader() ? header : entry.name());
        }
    }
}
