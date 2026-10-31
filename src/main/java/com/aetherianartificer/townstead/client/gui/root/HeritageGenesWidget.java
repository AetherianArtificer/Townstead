package com.aetherianartificer.townstead.client.gui.root;

import com.aetherianartificer.townstead.client.root.HeritageClientStore;
import com.aetherianartificer.townstead.root.HeritageRequestC2SPayload;
import com.aetherianartificer.townstead.root.HeritageSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** The gene rows of the Heritage screen for one entity, in the Roots tab's Genes view: what they carry, not their root's catalog. */
public class HeritageGenesWidget extends ScrollPane {

    private static final int ROW_H = 16;
    private static final int GAP = 3;

    @Nullable private UUID entity;
    @Nullable private HeritageSyncPayload.GeneRow hovered;

    public HeritageGenesWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    /** Shows this entity's genes, asking the server for a fresh copy. */
    public void show(@Nullable UUID entity) {
        if (entity == null || !entity.equals(this.entity)) resetScroll();
        this.entity = entity;
        if (entity == null) return;
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new HeritageRequestC2SPayload(entity));
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(new HeritageRequestC2SPayload(entity));
        *///?}
    }

    @Override
    protected int renderContent(GuiGraphics g, int left, int top, int innerW,
                                int innerTop, int innerBottom, int mouseX, int mouseY) {
        hovered = null;
        Minecraft mc = Minecraft.getInstance();
        HeritageSyncPayload data = HeritageClientStore.get(entity);
        String status = data == null ? "townstead.heritage.loading"
                : data.raceName().isEmpty() ? "townstead.heritage.unavailable"
                : data.genes().isEmpty() ? "townstead.origin.no_traits" : null;
        if (status != null) {
            g.drawString(mc.font, Component.translatable(status), left, top, 0xFF707070, false);
            return 11;
        }
        int y = top;
        for (HeritageSyncPayload.GeneRow row : data.genes()) {
            HeritageScreen.drawGeneRow(g, mc.font, row, left, y, innerW, mouseX, mouseY);
            if (mouseX >= left && mouseX <= left + innerW && mouseY >= y && mouseY <= y + ROW_H
                    && mouseY >= innerTop && mouseY < innerBottom) {
                hovered = row;
            }
            y += ROW_H + GAP;
        }
        return y - top - GAP;
    }

    @Override
    protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, int innerTop, int innerBottom) {
        if (hovered != null) {
            g.renderComponentTooltip(Minecraft.getInstance().font, HeritageScreen.heritageTooltip(hovered), mouseX, mouseY);
        }
    }
}
