package com.aetherianartificer.townstead.client.gui.root;

import com.aetherianartificer.townstead.client.gui.common.IconArt;
import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootMixNames;
import com.aetherianartificer.townstead.root.RootBrowseEntry;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import com.aetherianartificer.townstead.root.RootMix;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * The Root tab's summary of one root: its art and name, where it comes from, its backstory, the
 * facts players compare first (diet, activity, lifespan and whatever sets it apart) and its powers.
 * Hovering a power shows what it does.
 *
 * <p>For a mix it shows what the mix is called, each root's share, the heritage's backstory when
 * one matches, and the facts of the largest share, whose root sets the life cycle.</p>
 */
public class RootSummaryWidget extends ScrollPane {

    @Nullable private RootCatalogEntry root;
    @Nullable private RootMix mix;
    private final RootDetails.Hover hover = new RootDetails.Hover();

    public RootSummaryWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public void setRoot(@Nullable RootCatalogEntry root) {
        this.root = root;
        this.mix = null;
        resetScroll();
    }

    public void setMix(RootMix mix) {
        this.mix = mix;
        this.root = null;
        resetScroll();
    }

    @Override
    protected int renderContent(GuiGraphics g, int left, int top, int innerW,
                                int innerTop, int innerBottom, int mouseX, int mouseY) {
        hover.clear();
        if (mix != null) return renderMix(g, mix, left, top, innerW);
        if (root == null) return 0;
        RootBrowseEntry browse = RootCatalogClient.browse(root.id());
        int y = RootDetails.header(g, root, browse, left, top, innerW);
        y = RootDetails.backstory(g, root, left, y + 3, innerW, Integer.MAX_VALUE);
        y = RootDetails.facts(g, browse, left, y + 4, innerW);
        boolean inside = mouseY >= innerTop && mouseY < innerBottom;
        y = RootDetails.powers(g, browse, left, y + 4, innerW, inside ? mouseX : -1, mouseY, hover);
        return y - top;
    }

    private int renderMix(GuiGraphics g, RootMix mix, int left, int top, int w) {
        Font font = Minecraft.getInstance().font;
        RootCatalogEntry lead = RootCatalogClient.origin(mix.largest());
        RootMixNames.Named named = RootMixNames.get(mix);
        for (int i = Math.min(2, mix.members().size()) - 1; i >= 0; i--) {
            RootBrowseEntry art = RootCatalogClient.browse(mix.members().get(i).rootId());
            if (art != null && !art.icon().isEmpty()) IconArt.draw(g, art.icon(), left + i * 4, top + i * 8, 0.75f);
        }
        String name = named != null && !named.name().isEmpty() ? named.name() : lead != null ? lead.name() : "";
        g.drawString(font, RootDetails.trim(font, name, w - 20), left + 20, top, 0xFFFFFFFF, false);
        if (lead != null) {
            g.drawString(font, RootDetails.trim(font, RootDetails.crumb(lead, RootCatalogClient.browse(lead.id())), w - 20),
                    left + 20, top + 10, RootDetails.GOLD, false);
        }
        int y = top + 23;
        StringBuilder shares = new StringBuilder();
        for (RootMix.Member m : mix.members()) {
            RootCatalogEntry entry = RootCatalogClient.origin(m.rootId());
            if (!shares.isEmpty()) shares.append(" · ");
            shares.append(entry != null ? entry.name() : m.rootId()).append(' ').append(RootMix.shareLabel(m.quarters()));
        }
        for (FormattedCharSequence line : font.split(Component.literal(shares.toString()), w)) {
            g.drawString(font, line, left, y, 0xFF9A9A9A, false);
            y += 10;
        }
        if (named != null && !named.backstory().isEmpty()) {
            y += 3;
            for (FormattedCharSequence line : font.split(Component.literal(named.backstory()), w)) {
                g.drawString(font, line, left, y, 0xFFD8D8D8, false);
                y += 10;
            }
        }
        y = RootDetails.facts(g, RootCatalogClient.browse(mix.largest()), left, y + 4, w);
        return y - top;
    }

    @Override
    protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, int innerTop, int innerBottom) {
        hover.draw(g, Minecraft.getInstance().font, mouseX, mouseY);
    }
}
