package com.aetherianartificer.townstead.client.gui.common;

import com.aetherianartificer.townstead.spirit.SpiritRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;
import java.util.List;
import java.util.Map;

/** The catalog's original spirit item/color treatment, shared by definition inspectors. */
public final class CatalogSpiritChips {
    private CatalogSpiritChips() {}
    public record Result(int bottom, List<Component> tooltip) {}

    public static Result draw(GuiGraphics g, Font font, Map<String, Integer> points,
                              int x, int y, int width, int mx, int my) {
        int left = x;
        List<Component> tooltip = List.of();
        for (var spirit : SpiritRegistry.ordered()) {
            int value = points.getOrDefault(spirit.id(), 0);
            if (value <= 0) continue;
            String label = "+" + value;
            int w = 16 + font.width(label);
            if (x > left && x + w > left + width) { x = left; y += 13; }
            int color = spirit.color();
            g.fill(x, y, x + w, y + 11, (color & 0xFFFFFF) | 0x40000000);
            Palette.drawOutline(g, x, y, x + w, y + 11, (color & 0xFFFFFF) | 0xC0000000);
            g.pose().pushPose();
            g.pose().translate(x + 1, y + 1, 0);
            g.pose().scale(0.625f, 0.625f, 1);
            g.renderItem(new ItemStack(spirit.icon()), 0, 0);
            g.pose().popPose();
            g.drawString(font, label, x + 12, y + 2, color, false);
            if (new Controls.Rect(x, y, w, 11).contains(mx, my)) {
                tooltip = List.of(Component.translatable(spirit.displayKey()).withStyle(style -> style.withColor(color & 0xFFFFFF)),
                        Component.translatable("townstead.spirit.chip.tooltip", value).withStyle(ChatFormatting.GRAY));
            }
            x += w + 3;
        }
        return new Result(x == left ? y : y + 14, tooltip);
    }
}
