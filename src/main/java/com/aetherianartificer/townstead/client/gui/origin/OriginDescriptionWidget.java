package com.aetherianartificer.townstead.client.gui.origin;

import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * The description box: origin name, demonyms, lineage breadcrumb, and the full
 * wrapped backstory. Scrolls independently of the traits box.
 */
public class OriginDescriptionWidget extends ScrollPane {

    @Nullable private OriginCatalogEntry origin;

    public OriginDescriptionWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public void setOrigin(@Nullable OriginCatalogEntry origin) {
        this.origin = origin;
        resetScroll();
    }

    @Override
    protected int renderContent(GuiGraphics g, int left, int top, int innerW,
                                int innerTop, int innerBottom, int mouseX, int mouseY) {
        if (origin == null) return 0;
        Minecraft mc = Minecraft.getInstance();
        int cy = top;

        g.drawString(mc.font, origin.name(), left, cy, 0xFFE8E8E8, false);
        cy += 11;
        g.drawString(mc.font, "(" + origin.demonymSingular() + " / " + origin.demonymPlural() + ")",
                left, cy, 0xFF9A9A9A, false);
        cy += 11;
        String lineage = breadcrumb();
        if (!lineage.isEmpty()) {
            g.drawString(mc.font, lineage, left, cy, 0xFFB89A6A, false);
            cy += 11;
        }
        if (!origin.backstory().isEmpty()) {
            cy += 2;
            for (FormattedCharSequence line : mc.font.split(Component.literal(origin.backstory()), innerW)) {
                g.drawString(mc.font, line, left, cy, 0xFF8A8A8A);
                cy += 10;
            }
        }
        return cy - top;
    }

    private String breadcrumb() {
        if (origin == null) return "";
        StringBuilder sb = new StringBuilder();
        if (!origin.speciesName().isEmpty()) sb.append(origin.speciesName());
        String second = !origin.heritageName().isEmpty() ? origin.heritageName() : origin.ancestryName();
        if (!second.isEmpty()) {
            if (sb.length() > 0) sb.append(" > ");
            sb.append(second);
        }
        return sb.toString();
    }
}
