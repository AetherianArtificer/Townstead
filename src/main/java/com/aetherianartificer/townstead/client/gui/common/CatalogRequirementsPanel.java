package com.aetherianartificer.townstead.client.gui.common;

import com.aetherianartificer.townstead.client.catalog.RequirementNameResolver;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** The same ingredient rows for building requirements and each alternative decoration assembly. */
public final class CatalogRequirementsPanel {
    public record Row(List<String> selectors, int count, boolean anchor) {
        public Row { selectors = List.copyOf(selectors); }
    }
    private CatalogRequirementsPanel() {}
    public static int draw(GuiGraphics g, Font font, List<Row> rows, int x, int y, int width, long ticker) {
        int salt = 0;
        for (Row row : rows) {
            if (row.selectors().isEmpty()) continue;
            String selector = row.selectors().get((int) Math.floorMod(ticker / 40, row.selectors().size()));
            ResourceLocation id = ResourceLocation.tryParse(selector.startsWith("#") ? selector.substring(1) : selector);
            String name = selector;
            if (id != null) {
                var icon = RequirementNameResolver.displayIcon(id, ticker, salt++);
                icon.render(g, x, y);
                name = icon.label().isEmpty() ? RequirementNameResolver.displayName(id) : icon.label();
            }
            String text = row.count() + "× " + name;
            if (row.anchor()) text += " · " + Component.translatable("townstead.catalog.anchor").getString();
            int h = Math.max(18, font.split(Component.literal(text), width - 22).size() * font.lineHeight + 3);
            g.drawWordWrap(font, Component.literal(text), x + 21, y + 2, width - 22, 0xC4D7DB);
            y += h;
            if (row.selectors().size() > 1) {
                String options = String.join(" / ", row.selectors().stream().map(raw -> {
                    var key = ResourceLocation.tryParse(raw.startsWith("#") ? raw.substring(1) : raw);
                    return key == null ? raw : RequirementNameResolver.displayName(key);
                }).toList());
                Component hint = Component.translatable("townstead.catalog.any_anchor", options);
                g.drawWordWrap(font, hint, x + 21, y, width - 22, 0x97AB9E);
                y += font.split(hint, width - 22).size() * font.lineHeight + 3;
            }
        }
        return y;
    }
}
