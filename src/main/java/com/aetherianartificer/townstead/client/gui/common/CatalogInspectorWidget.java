package com.aetherianartificer.townstead.client.gui.common;

import com.aetherianartificer.townstead.client.catalog.*;
import com.aetherianartificer.townstead.spirit.BuildingSpiritIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** A scrollable inspector; nothing is silently clipped when descriptions/variants grow. */
public final class CatalogInspectorWidget extends AbstractWidget {
    private final Font font;
    private CatalogEntries.Display selected;
    private int scroll, summaryScroll, contentHeight, summaryHeight, variant;
    private boolean draggingScrollbar;
    private float uiScale = 1;
    private List<Component> spiritTooltip = List.of();
    private CatalogDataLoader.Theme theme = CatalogDataLoader.Theme.DEFAULT;
    public void uiScale(float scale) { uiScale = scale; }
    public void theme(CatalogDataLoader.Theme value) { theme = value; }
    private int dividerY() { return getY() + height * 46 / 100; }
    private int requirementsTop() { return dividerY() + (variantCount() > 1 ? 44 : 22); }
    private int requirementsBottom() { return getY() + height - (selected != null && selected.building() != null ? 32 : 5); }
    private int requirementsHeight() { return Math.max(0, requirementsBottom() - requirementsTop()); }
    private int maxScroll() { return Math.max(0, contentHeight - requirementsHeight()); }
    public int variantControlsY() { return dividerY() + 20; }
    public CatalogInspectorWidget(Font font, int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("townstead.catalog.details")); this.font = font;
    }
    public void select(CatalogEntries.Display next) {
        if (selected == null || next == null || !selected.entry().id().equals(next.entry().id())) {
            scroll = 0; summaryScroll = 0; variant = 0; contentHeight = 0; draggingScrollbar = false;
        }
        selected = next;
        if (next != null) setMessage(Component.literal(next.entry().name()));
        variant = Math.min(variant, Math.max(0, variantCount() - 1));
    }
    public int variantCount() { return selected == null || selected.decoration() == null ? 0 : selected.decoration().variants().size(); }
    public int variant() { return variant; }
    public void changeVariant(int delta) {
        variant = Math.floorMod(variant + delta, Math.max(1, variantCount()));
        scroll = 0; contentHeight = 0; draggingScrollbar = false;
    }
    private int text(GuiGraphics g, Component text, int y, int color) {
        g.drawWordWrap(font, text, getX() + 7, y, width - 18, color);
        return y + Math.max(1, font.split(text, width - 18).size()) * font.lineHeight + 5;
    }
    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
        spiritTooltip = List.of();
        g.fill(getX(), getY(), getX() + width, getY() + height, isFocused() ? theme.frameColor() : theme.borderColor());
        g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, theme.detailsBackgroundColor());
        if (selected == null) {
            text(g, Component.translatable("townstead.catalog.select_entry"), getY() + 10, 0xAFBCAA); return;
        }
        ScaledWidget.scissor(g, uiScale, getX() + 2, getY() + 2, getX() + width - 3, dividerY() - 3);
        int start = getY() + 7 - summaryScroll;
        int y = text(g, Component.literal(selected.entry().name()), start, 0xFFF1E6C9);
        var entry = selected.entry();
        String family = entry.groupLabel();
        if (entry.tier() > 0) family = Component.translatable("townstead.catalog.tier", CatalogGraphLayout.roman(entry.tier())).getString() + " · " + family;
        y = text(g, Component.literal(family), y, 0xD6C381);
        if (!selected.modName().isEmpty()) y = text(g, Component.literal(selected.modName()), y, 0x8FC1FF);
        if (entry.hangout()) {
            CatalogBadgeRenderer.hangoutLabel(g, font, Component.translatable("townstead.catalog.hangout").getString(), getX() + 7, y, 0xADBEAF);
            y += 17;
        }
        if (selected.decoration() != null) {
            y = text(g, selected.decoration().recognized() > 0
                    ? Component.translatable("townstead.catalog.decoration_recognized", selected.decoration().recognized())
                    : Component.translatable("townstead.catalog.not_recognized"), y, 0xA5C9A8);
        } else {
            y = text(g, Component.translatable(entry.recognized() ? "townstead.catalog.recognized" : "townstead.catalog.not_recognized"), y, 0xA5C9A8);
        }
        var contributions = selected.decoration() == null ? BuildingSpiritIndex.contributionsFor(entry.id()) : selected.decoration().spirits();
        var chips = CatalogSpiritChips.draw(g, font, contributions, getX() + 7, y, width - 18, mx, my,
                selected.decoration() == null ? "townstead.spirit.chip.tooltip" : "townstead.spirit.chip.decoration_tooltip");
        y = chips.bottom();
        if (mx >= getX() + 2 && mx < getX() + width - 3 && my >= getY() + 2 && my < dividerY() - 3) {
            spiritTooltip = chips.tooltip();
        }
        y += 3;
        y = text(g, selected.description(), y, 0xB4BCAF);
        summaryHeight = y - start + 10;
        summaryScroll = Math.min(summaryScroll, Math.max(0, summaryHeight - (dividerY() - getY())));
        g.disableScissor();
        g.fill(getX() + 1, dividerY(), getX() + width - 1, dividerY() + 1, theme.borderColor());
        int requirementsTop = requirementsTop();
        g.drawString(font, Component.translatable("townstead.configuration.needs"), getX() + 7, dividerY() + 7, 0xEEE7D4, false);
        int bottom = requirementsBottom();
        ScaledWidget.scissor(g, uiScale, getX() + 2, requirementsTop, getX() + width - 3, bottom);
        y = requirementsTop - scroll;
        int requirementsStart = y;
        List<CatalogRequirementsPanel.Row> rows = new ArrayList<>();
        if (selected.building() != null) {
            selected.building().getGroups().entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
                    .forEach(e -> rows.add(new CatalogRequirementsPanel.Row(List.of(e.getKey().toString()), e.getValue())));
        } else if (variantCount() > 0) {
            var recipe = selected.decoration().variants().get(variant);
            rows.add(new CatalogRequirementsPanel.Row(recipe.anchors(), 1));
            for (var row : recipe.requirements()) rows.add(new CatalogRequirementsPanel.Row(List.of(row.selector()), row.count()));
        }
        var level = Minecraft.getInstance().level;
        y = CatalogRequirementsPanel.draw(g, font, rows, getX() + 7, y, width - 18,
                level == null ? System.currentTimeMillis() / 50 : level.getGameTime());
        contentHeight = y - requirementsStart + 4;
        scroll = Math.min(scroll, maxScroll());
        g.disableScissor();
        Controls.drawScrollbar(g, getX() + width - 6, requirementsTop, bottom - requirementsTop,
                scroll, bottom - requirementsTop, contentHeight);
    }
    /** Render after the inspector's scissor and child controls, so hover text stays readable. */
    public void renderTooltip(GuiGraphics g, int mx, int my) {
        if (!spiritTooltip.isEmpty()) g.renderComponentTooltip(font, spiritTooltip, mx, my);
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        draggingScrollbar = false;
        if (button != 0 || !isMouseOver(mx, my)) return false;
        int picked = Controls.scrollbarPick(getX() + width - Controls.SCROLLBAR_W,
                requirementsTop(), requirementsHeight(), mx, my, requirementsHeight(), contentHeight);
        if (picked >= 0) {
            scroll = picked;
            draggingScrollbar = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button != 0 || !draggingScrollbar) return false;
        scroll = Controls.scrollbarDrag(requirementsTop(), requirementsHeight(), my, requirementsHeight(), contentHeight);
        return true;
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0 || !draggingScrollbar) return false;
        draggingScrollbar = false;
        return true;
    }
    public boolean scroll(double x, double y, double amount) {
        if (!isMouseOver(x, y)) return false;
        if (y < dividerY()) summaryScroll = Math.max(0, Math.min(Math.max(0, summaryHeight - (dividerY() - getY())), summaryScroll - (int) (amount * 18)));
        else scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (amount * 18)));
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        int delta = key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_UP ? -height / 2
                : key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_DOWN ? height / 2 : 0;
        if (delta == 0) return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll + delta)); return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        if (selected != null) output.add(NarratedElementType.HINT, selected.description());
    }
}
