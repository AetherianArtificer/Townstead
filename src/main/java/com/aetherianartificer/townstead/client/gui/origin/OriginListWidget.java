package com.aetherianartificer.townstead.client.gui.origin;

import com.aetherianartificer.townstead.client.origin.OriginClientStore;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Consumer;

/**
 * Scrollable origin picker, shared by the Villager Editor and Destiny pages.
 * Lists {@link com.aetherianartificer.townstead.client.origin.OriginCatalogClient}
 * entries; each row shows the name, plural demonym, and a couple of wrapped
 * backstory lines. The row matching the target's current origin (read live from
 * {@link OriginClientStore}) is marked. Selecting a row fires {@code onSelect}.
 *
 * <p>Cross-version {@code ObjectSelectionList} handling mirrors
 * {@code fieldpost/ToolPaletteList}.</p>
 */
public class OriginListWidget extends ObjectSelectionList<OriginListWidget.OriginEntry> {

    private static final int ROW_HEIGHT = 46;
    private final int xPos;
    private final int targetEntityId;
    private final Consumer<String> onSelect;

    //? if >=1.21 {
    public OriginListWidget(Minecraft mc, int x, int width, int height, int y,
                            int targetEntityId, Consumer<String> onSelect) {
        super(mc, width, height, y, ROW_HEIGHT);
        this.xPos = x;
        this.targetEntityId = targetEntityId;
        this.onSelect = onSelect;
        this.setX(x);
    }
    //?} else {
    /*public OriginListWidget(Minecraft mc, int x, int width, int height, int top,
                            int targetEntityId, Consumer<String> onSelect) {
        super(mc, width, height, top, top + height, ROW_HEIGHT);
        this.xPos = x;
        this.targetEntityId = targetEntityId;
        this.onSelect = onSelect;
        this.x0 = x;
        this.x1 = x + width;
        setRenderBackground(false);
    }
    *///?}

    public void setEntries(List<OriginCatalogEntry> entries) {
        clearEntries();
        for (OriginCatalogEntry entry : entries) {
            addEntry(new OriginEntry(this, entry));
        }
    }

    String currentOriginId() {
        String id = OriginClientStore.get(targetEntityId);
        // Until the server replies, treat unset as the default — everyone is an
        // Overworlder by default, so the row highlights without a flash.
        return id.isEmpty() ? com.aetherianartificer.townstead.origin.OriginRegistry.DEFAULT_ID.toString() : id;
    }

    @Override
    public void setSelected(OriginEntry entry) {
        super.setSelected(entry);
        if (entry != null && onSelect != null) {
            onSelect.accept(entry.entry.id());
        }
    }

    @Override
    public int getRowWidth() {
        return this.width - 16;
    }

    @Override
    protected int getScrollbarPosition() {
        return xPos + this.width - 6;
    }

    /** Opaque panel + border so the picker doesn't render over the editor's model. */
    private static void drawPanel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, 0xF0141414);
        g.fill(x0, y0, x1, y0 + 1, 0xFF000000);
        g.fill(x0, y1 - 1, x1, y1, 0xFF000000);
        g.fill(x0, y0, x0 + 1, y1, 0xFF000000);
        g.fill(x1 - 1, y0, x1, y1, 0xFF000000);
    }

    //? if >=1.21 {
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.getX() && mouseX < this.getX() + this.width
                && mouseY >= this.getY() && mouseY < this.getY() + this.getHeight();
    }

    @Override
    protected void renderListBackground(GuiGraphics g) {
        drawPanel(g, this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.getHeight());
    }

    @Override
    protected void renderListSeparators(GuiGraphics g) {}
    //?} else {
    /*@Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        drawPanel(g, this.x0, this.y0, this.x1, this.y1);
        g.enableScissor(this.x0, this.y0, this.x1, this.y1);
        super.render(g, mouseX, mouseY, partial);
        g.disableScissor();
    }
    *///?}

    public static class OriginEntry extends ObjectSelectionList.Entry<OriginEntry> {
        private final OriginListWidget parent;
        private final OriginCatalogEntry entry;

        OriginEntry(OriginListWidget parent, OriginCatalogEntry entry) {
            this.parent = parent;
            this.entry = entry;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            boolean current = entry.id().equals(parent.currentOriginId());

            int pad = 4;
            int innerLeft = left + pad;
            int innerWidth = width - pad * 2;

            if (current) {
                g.fill(left, top, left + width, top + height - 2, 0x553B7A3B);
                g.fill(left, top, left + 2, top + height - 2, 0xFF6FCF6F);
            } else if (hovered) {
                g.fill(left, top, left + width, top + height - 2, 0x33FFFFFF);
            }

            int nameColor = current ? 0xFFB8F0B8 : (hovered ? 0xFFFFFFFF : 0xFFE0E0E0);
            g.drawString(mc.font, entry.name(), innerLeft, top + 4, nameColor, false);

            int nameW = mc.font.width(entry.name());
            String demonym = "(" + entry.demonymPlural() + ")";
            g.drawString(mc.font, demonym, innerLeft + nameW + 6, top + 4, 0xFF9A9A9A, false);

            if (!entry.backstory().isEmpty()) {
                List<FormattedCharSequence> lines = mc.font.split(Component.literal(entry.backstory()), innerWidth);
                int maxLines = Math.min(2, lines.size());
                for (int i = 0; i < maxLines; i++) {
                    g.drawString(mc.font, lines.get(i), innerLeft, top + 16 + i * 10, 0xFF8A8A8A);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                parent.setSelected(this);
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            return Component.literal(entry.name());
        }
    }
}
