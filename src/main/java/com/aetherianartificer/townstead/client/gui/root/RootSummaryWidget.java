package com.aetherianartificer.townstead.client.gui.root;

import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.root.RootBrowseEntry;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * The Root tab's summary of one root: its art and name, where it comes from, its backstory, the
 * facts players compare first (diet, activity, lifespan and whatever sets it apart) and its powers.
 * Hovering a power shows what it does.
 */
public class RootSummaryWidget extends ScrollPane {

    @Nullable private RootCatalogEntry root;
    private final RootDetails.Hover hover = new RootDetails.Hover();

    public RootSummaryWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public void setRoot(@Nullable RootCatalogEntry root) {
        this.root = root;
        resetScroll();
    }

    @Override
    protected int renderContent(GuiGraphics g, int left, int top, int innerW,
                                int innerTop, int innerBottom, int mouseX, int mouseY) {
        hover.clear();
        if (root == null) return 0;
        RootBrowseEntry browse = RootCatalogClient.browse(root.id());
        int y = RootDetails.header(g, root, browse, left, top, innerW);
        y = RootDetails.backstory(g, root, left, y + 3, innerW, Integer.MAX_VALUE);
        y = RootDetails.facts(g, browse, left, y + 4, innerW);
        boolean inside = mouseY >= innerTop && mouseY < innerBottom;
        y = RootDetails.powers(g, browse, left, y + 4, innerW, inside ? mouseX : -1, mouseY, hover);
        return y - top;
    }

    @Override
    protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, int innerTop, int innerBottom) {
        hover.draw(g, Minecraft.getInstance().font, mouseX, mouseY);
    }
}
