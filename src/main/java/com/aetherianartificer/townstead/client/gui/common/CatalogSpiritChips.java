package com.aetherianartificer.townstead.client.gui.common;

import com.aetherianartificer.townstead.spirit.SpiritRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

/** The catalog's original spirit item/color treatment, shared by definition inspectors. */
public final class CatalogSpiritChips {
    private CatalogSpiritChips() {}
    public static int draw(GuiGraphics g, Font font, Map<String, Integer> points, int x, int y, int width) {
        int left = x;
        for (var spirit : SpiritRegistry.ordered()) {
            int value = points.getOrDefault(spirit.id(), 0);
            if (value <= 0) continue;
            String name = Component.translatable(spirit.displayKey()).getString();
            int w = Math.min(width, Math.max(85, font.width(name) + 30));
            if (x > left && x + w > left + width) { x = left; y += 29; }
            int color = spirit.color();
            g.fill(x, y, x + w, y + 26, color);
            g.fill(x + 1, y + 1, x + w - 1, y + 25, 0xFF14232A);
            g.fill(x + 1, y + 1, x + w - 1, y + 25, (color & 0xFFFFFF) | 0x28000000);
            g.renderItem(new ItemStack(spirit.icon()), x + 4, y + 5);
            g.drawString(font, font.plainSubstrByWidth(name, w - 26), x + 24, y + 4, color, false);
            g.drawString(font, "+" + value, x + 24, y + 14, color, false);
            x += w + 4;
        }
        return x == left ? y : y + 30;
    }
}
