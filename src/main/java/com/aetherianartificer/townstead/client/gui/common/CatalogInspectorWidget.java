package com.aetherianartificer.townstead.client.gui.common;

import com.aetherianartificer.townstead.client.catalog.*;
import com.aetherianartificer.townstead.spirit.BuildingSpiritIndex;
import com.aetherianartificer.townstead.spirit.SpiritRegistry;
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
    private float uiScale = 1;
    private CatalogDataLoader.Theme theme = CatalogDataLoader.Theme.DEFAULT;
    public void uiScale(float scale) { uiScale = scale; }
    public void theme(CatalogDataLoader.Theme value) { theme = value; }
    private int dividerY() { return getY() + height * 46 / 100; }
    public int variantControlsY() { return dividerY() + 20; }
    public CatalogInspectorWidget(Font font, int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("townstead.catalog.details")); this.font = font;
    }
    public void select(CatalogEntries.Display next) {
        if (selected == null || next == null || !selected.entry().id().equals(next.entry().id())) { scroll = 0; summaryScroll = 0; variant = 0; }
        selected = next;
        if (next != null) setMessage(Component.literal(next.entry().name()));
        variant = Math.min(variant, Math.max(0, variantCount() - 1));
    }
    public int variantCount() { return selected == null || selected.decoration() == null ? 0 : selected.decoration().variants().size(); }
    public int variant() { return variant; }
    public void changeVariant(int delta) { variant = Math.floorMod(variant + delta, Math.max(1, variantCount())); scroll = 0; }
    private int text(GuiGraphics g, Component text, int y, int color) {
        g.drawWordWrap(font, text, getX() + 7, y, width - 18, color);
        return y + Math.max(1, font.split(text, width - 18).size()) * font.lineHeight + 5;
    }
    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
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
        if (entry.hangout()) {
            CatalogBadgeRenderer.badge(g, font, Component.translatable("townstead.catalog.hangout").getString(), getX() + 7, y, 0xFFE4C475);
            y += 17;
        }
        if (selected.decoration() != null) {
            y = text(g, Component.translatable("townstead.catalog.decoration_recognized", selected.decoration().recognized()), y, 0xA5C9A8);
            y = text(g, Component.translatable("townstead.catalog.radius", selected.decoration().radius()), y, 0xC7C5AF);
        } else {
            y = text(g, Component.translatable(entry.recognized() ? "townstead.catalog.recognized" : "townstead.catalog.not_recognized"), y, 0xA5C9A8);
            y = CatalogSpiritChips.draw(g, font, BuildingSpiritIndex.contributionsFor(entry.id()), getX() + 7, y, width - 18);
        }
        y += 3;
        y = text(g, selected.description(), y, 0xB4BCAF);
        summaryHeight = y - start + 10;
        summaryScroll = Math.min(summaryScroll, Math.max(0, summaryHeight - (dividerY() - getY())));
        g.disableScissor();
        g.fill(getX() + 1, dividerY(), getX() + width - 1, dividerY() + 1, theme.borderColor());
        int requirementsTop = dividerY() + (variantCount() > 1 ? 44 : 22);
        g.drawString(font, Component.translatable(selected.decoration() == null
                ? "townstead.configuration.needs" : "townstead.catalog.assembly"), getX() + 7, dividerY() + 7, 0xEEE7D4, false);
        int bottom = getY() + height - (selected.building() == null ? 5 : 32);
        ScaledWidget.scissor(g, uiScale, getX() + 2, requirementsTop, getX() + width - 3, bottom);
        y = requirementsTop - scroll;
        int requirementsStart = y;
        List<CatalogRequirementsPanel.Row> rows = new ArrayList<>();
        if (selected.building() != null) {
            selected.building().getGroups().entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
                    .forEach(e -> rows.add(new CatalogRequirementsPanel.Row(List.of(e.getKey().toString()), e.getValue(), false)));
        } else if (variantCount() > 0) {
            var recipe = selected.decoration().variants().get(variant);
            rows.add(new CatalogRequirementsPanel.Row(recipe.anchors(), 1, true));
            for (var row : recipe.requirements()) rows.add(new CatalogRequirementsPanel.Row(List.of(row.selector()), row.count(), false));
        }
        var level = Minecraft.getInstance().level;
        y = CatalogRequirementsPanel.draw(g, font, rows, getX() + 7, y, width - 18,
                level == null ? System.currentTimeMillis() / 50 : level.getGameTime());
        if (selected.decoration() != null) {
            y += 7;
            if (variantCount() > 1) y = text(g, Component.translatable("townstead.catalog.one_variant"), y, 0xA4B2A3);
            y = text(g, Component.translatable("townstead.catalog.radius_hint", selected.decoration().radius()), y, 0xA4B2A3);
        }
        contentHeight = y - requirementsStart + 4;
        scroll = Math.min(scroll, Math.max(0, contentHeight - (bottom - requirementsTop)));
        g.disableScissor();
        Controls.drawScrollbar(g, getX() + width - 6, requirementsTop, bottom - requirementsTop,
                scroll, bottom - requirementsTop, contentHeight);
    }
    public boolean scroll(double x, double y, double amount) {
        if (!isMouseOver(x, y)) return false;
        if (y < dividerY()) summaryScroll = Math.max(0, Math.min(Math.max(0, summaryHeight - (dividerY() - getY())), summaryScroll - (int) (amount * 18)));
        else scroll = Math.max(0, Math.min(Math.max(0, contentHeight - (getY() + height - dividerY() - 55)), scroll - (int) (amount * 18)));
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        int delta = key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_UP ? -height / 2
                : key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_DOWN ? height / 2 : 0;
        if (delta == 0) return false;
        scroll = Math.max(0, Math.min(Math.max(0, contentHeight - (height / 2 - 55)), scroll + delta)); return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        if (selected != null) output.add(NarratedElementType.HINT, selected.description());
    }
}
