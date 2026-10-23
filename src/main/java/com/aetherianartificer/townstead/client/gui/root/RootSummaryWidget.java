package com.aetherianartificer.townstead.client.gui.root;

import com.aetherianartificer.townstead.client.gui.common.IconArt;
import com.aetherianartificer.townstead.client.root.RootCatalogClient;
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
    private String mixName = "";
    private String mixBackstory = "";
    @Nullable private Component status;
    private final RootDetails.Hover hover = new RootDetails.Hover();

    public RootSummaryWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public void setRoot(@Nullable RootCatalogEntry root) {
        this.root = root;
        this.mix = null;
        resetScroll();
    }

    /** A mix, with what the server called it (empty while unknown) and its heritage's backstory. */
    public void setMix(RootMix mix, String name, String backstory) {
        this.mix = mix;
        this.mixName = name;
        this.mixBackstory = backstory;
        this.root = null;
        resetScroll();
    }

    /** A line in place of everything else ("Loading..."), or null to show the root or mix. */
    public void setStatus(@Nullable Component status) {
        this.status = status;
    }

    @Override
    protected int renderContent(GuiGraphics g, int left, int top, int innerW,
                                int innerTop, int innerBottom, int mouseX, int mouseY) {
        hover.clear();
        if (status != null) {
            g.drawString(Minecraft.getInstance().font, status, left, top, 0xFF9A9A9A, false);
            return 10;
        }
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
        for (int i = Math.min(2, mix.members().size()) - 1; i >= 0; i--) {
            RootBrowseEntry art = RootCatalogClient.browse(mix.members().get(i).rootId());
            if (art != null && !art.icon().isEmpty()) IconArt.draw(g, art.icon(), left + i * 4, top + i * 8, 0.75f);
        }
        String name = !mixName.isEmpty() ? mixName : lead != null ? lead.name() : "";
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
        if (!mixBackstory.isEmpty()) {
            y += 3;
            for (FormattedCharSequence line : font.split(Component.literal(mixBackstory), w)) {
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
