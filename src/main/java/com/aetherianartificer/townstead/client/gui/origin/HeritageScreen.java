package com.aetherianartificer.townstead.client.gui.origin;

import com.aetherianartificer.townstead.client.origin.HeritageClientStore;
import com.aetherianartificer.townstead.client.origin.OriginCatalogClient;
import com.aetherianartificer.townstead.origin.GeneCatalogEntry;
import com.aetherianartificer.townstead.origin.HeritageRequestC2SPayload;
import com.aetherianartificer.townstead.origin.HeritageSyncPayload;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-only "Heritage" view for a villager, opened from the interaction screen. It
 * leads with the realized race name and the ancestry-fraction bar (the 23andMe
 * readout), then breaks the genotype down into diploid gene rows showing the
 * expressed allele and any recessively-carried one. Data arrives via
 * {@link HeritageSyncPayload}, requested on open and read from {@link HeritageClientStore}.
 */
public class HeritageScreen extends Screen {

    private static final int PANEL_W = 232;
    private static final int PAD = 10;
    private static final int ROW_H = 16;
    private static final int GAP = 3;

    private final UUID villagerUuid;
    private final Screen parent;

    public HeritageScreen(VillagerLike<?> villager) {
        this(villager.asEntity().getUUID(), Minecraft.getInstance().screen);
    }

    public HeritageScreen(UUID villagerUuid, Screen parent) {
        super(Component.translatable("townstead.heritage.title"));
        this.villagerUuid = villagerUuid;
        this.parent = parent;
    }

    public static void open(VillagerLike<?> villager) {
        if (villager == null) return;
        open(villager.asEntity().getUUID(), Minecraft.getInstance().screen);
    }

    /** Open for a villager by UUID, carrying the parent the Done button returns to (the interaction screen). */
    public static void open(UUID villagerUuid, Screen parent) {
        if (villagerUuid == null) return;
        Minecraft.getInstance().setScreen(new HeritageScreen(villagerUuid, parent));
    }

    @Override
    protected void init() {
        super.init();
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new HeritageRequestC2SPayload(villagerUuid));
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(new HeritageRequestC2SPayload(villagerUuid));
        *///?}
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 50, height - 25, 100, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Background + widgets FIRST, then our panel on top. On 1.21 super.render()
        // itself calls renderBackground() (the blur), so calling it again afterwards
        // would blur the panel; on 1.20.1 super.render() is widgets-only, so we dim
        // explicitly. Either way the panel is drawn last and stays sharp.
        //? if neoforge {
        super.render(g, mouseX, mouseY, partialTick);
        //?} else {
        /*renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        *///?}
        HeritageSyncPayload data = HeritageClientStore.get(villagerUuid);
        Font font = this.font;

        if (data == null) {
            g.drawCenteredString(font, Component.translatable("townstead.heritage.loading"),
                    width / 2, height / 2 - 20, 0xFFAAAAAA);
            return;
        }
        if (data.raceName().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("townstead.heritage.unavailable"),
                    width / 2, height / 2 - 20, 0xFFAAAAAA);
            return;
        }

        // Measure content height: header (2 lines) + ancestry block + genes block.
        int ancestryRows = data.ancestry().size();
        int geneRows = data.genes().size();
        int contentH = 14 + 12                                   // race name + origin subtitle
                + 12 + 13 + ancestryRows * 11                     // "Ancestry" + stacked bar + legend
                + (geneRows > 0 ? 5 + 12 + geneRows * (ROW_H + GAP) : 0); // gap + "Genes" label + rows
        int panelH = contentH + PAD * 2;

        int x0 = (width - PANEL_W) / 2;
        int y0 = Math.max(24, (height - panelH) / 2 - 10);
        int x1 = x0 + PANEL_W;
        OriginPanel.draw(g, x0, y0, x1, y0 + panelH);

        int centerX = (x0 + x1) / 2;
        int y = y0 + PAD;

        // Headline: heritage name, then the supporting assignment-profile/lineage line.
        g.drawCenteredString(font, Component.literal(data.raceName()), centerX, y, 0xFFFFFFFF);
        y += 13;
        if (!data.originName().isEmpty() && !data.originName().equals(data.raceName())) {
            g.drawCenteredString(font, Component.translatable("townstead.heritage.of_line", data.originName()),
                    centerX, y, 0xFF9A9A9A);
        }
        y += 14;

        // Ancestry: one stacked bar split by fraction (colourblind-safe palette), then a
        // text legend so the blend is readable without relying on colour at all.
        int left = x0 + PAD;
        int innerW = PANEL_W - PAD * 2;
        g.drawString(font, Component.translatable("townstead.heritage.ancestry"), left, y, 0xFFC8C8C8, false);
        y += 12;
        List<HeritageSyncPayload.AncestryShare> shares = data.ancestry();
        int barH = 9;
        g.fill(left, y, left + innerW, y + barH, 0xFF0A0A0A);
        int cx = left;
        for (int i = 0; i < shares.size(); i++) {
            HeritageSyncPayload.AncestryShare a = shares.get(i);
            int segEnd = (i == shares.size() - 1) ? left + innerW : cx + Math.round(a.fraction() * innerW);
            segEnd = Math.min(left + innerW, Math.max(cx + 1, segEnd));
            g.fill(cx, y, segEnd, y + barH, ancestryColor(a.name()));
            if (i > 0) g.fill(cx, y, cx + 1, y + barH, 0xFF000000); // divider, distinct without colour
            cx = segEnd;
        }
        GeneVisuals.drawBorder(g, left, y, left + innerW, y + barH, 0xFF101010, false);
        y += barH + 4;
        for (HeritageSyncPayload.AncestryShare a : shares) {
            int sw = 7;
            g.fill(left, y + 1, left + sw, y + 1 + sw, ancestryColor(a.name()));
            g.fill(left, y + 1, left + sw, y + 2, 0x40FFFFFF);
            g.drawString(font, GeneVisuals.truncate(font, a.name(), innerW - sw - 40), left + sw + 4, y + 1, 0xFFE6E6E6, false);
            String pct = Math.round(a.fraction() * 100) + "%";
            g.drawString(font, pct, left + innerW - font.width(pct), y + 1, 0xFFE6E6E6, false);
            y += 11;
        }

        // Gene rows: expressed value + carrier dot; full stat-block tooltip on hover.
        HeritageSyncPayload.GeneRow hoveredRow = null;
        if (geneRows > 0) {
            y += 5; // breathing room between the ancestry block and the genes list
            g.drawString(font, Component.translatable("townstead.heritage.genes"), left, y, 0xFFC8C8C8, false);
            y += 12;
            for (HeritageSyncPayload.GeneRow row : data.genes()) {
                drawGeneRow(g, font, row, left, y, innerW, mouseX, mouseY);
                if (mouseX >= left && mouseX <= left + innerW && mouseY >= y && mouseY <= y + ROW_H) {
                    hoveredRow = row;
                }
                y += ROW_H + GAP;
            }
        }

        // Tooltip last, so it sits above the panel.
        if (hoveredRow != null) {
            g.renderComponentTooltip(font, heritageTooltip(hoveredRow), mouseX, mouseY);
        }
    }

    private void drawGeneRow(GuiGraphics g, Font font, HeritageSyncPayload.GeneRow row,
                             int left, int top, int innerW, int mouseX, int mouseY) {
        int x1 = left + innerW;
        int y1 = top + ROW_H;
        boolean hovered = mouseX >= left && mouseX <= x1 && mouseY >= top && mouseY <= y1;
        int tint = GeneVisuals.categoryTint(row.category());
        GeneVisuals.drawStoneButton(g, left, top, x1, y1, tint, hovered);
        // Dashed border for a recessive gene, solid for dominant — same as the Origins menu.
        GeneCatalogEntry def = OriginCatalogClient.gene(row.geneId());
        GeneVisuals.drawBorder(g, left, top, x1, y1, tint, def != null && def.isRecessive());

        int textY = top + (ROW_H - 8) / 2;
        int contentX = left + 5;
        int nameX;
        if (GeneVisuals.hasCategoryIcon(row.category())) {
            GeneVisuals.drawCategoryIcon(g, row.category(), contentX, top + (ROW_H - GeneVisuals.ICON_SIZE) / 2);
            nameX = contentX + GeneVisuals.ICON_SIZE + 5;
        } else {
            nameX = contentX;
        }

        // Right side: the expressed variant (multi-variant genes only) and a small
        // dot when a hidden recessive copy is carried. The dot's meaning is in the tooltip.
        int rightEdge = x1 - 5;
        if (!row.carries().isEmpty()) {
            int dotSize = 3;
            int dotX = rightEdge - dotSize;
            int dotY = top + (ROW_H - dotSize) / 2;
            g.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, 0xFFB58CD8);
            rightEdge = dotX - 4;
        }
        if (!row.variant().isEmpty()) {
            int vx = rightEdge - font.width(row.variant());
            g.drawString(font, row.variant(), vx, textY, 0xFFFFFFFF, false);
            rightEdge = vx;
        }

        int nameMaxW = rightEdge - 4 - nameX;
        String label = nameMaxW > 8 ? GeneVisuals.truncate(font, row.label(), nameMaxW) : row.label();
        g.drawString(font, label, nameX, textY, hovered ? 0xFFFFFFFF : 0xFFE6E6E6, false);
    }

    /** Full stat-block tooltip for a gene row (same shape as the Origins genes menu). */
    private List<Component> heritageTooltip(HeritageSyncPayload.GeneRow row) {
        List<Component> lines = new ArrayList<>();
        GeneCatalogEntry def = OriginCatalogClient.gene(row.geneId());
        String name = def != null && !def.name().isEmpty() ? def.name() : row.label();
        lines.add(Component.literal(name).withStyle(ChatFormatting.WHITE));
        if (def != null && !def.description().isEmpty()) {
            lines.add(Component.literal(def.description()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.empty());
        lines.add(Component.translatable("townstead.heritage.tooltip.category",
                Component.literal(row.category())).withStyle(ChatFormatting.DARK_GRAY));
        boolean recessive = def != null && def.isRecessive();
        lines.add(Component.translatable("townstead.heritage.tooltip.dominance",
                        Component.translatable(recessive ? "townstead.heritage.tooltip.recessive"
                                        : "townstead.heritage.tooltip.dominant")
                                .withStyle(recessive ? ChatFormatting.GRAY : ChatFormatting.GOLD))
                .withStyle(ChatFormatting.DARK_GRAY));

        // Only mention zygosity when the two copies diverge (a carried recessive).
        // A homozygous gene is simply "what it is" — already shown by the row.
        String shown = row.variant().isEmpty() ? name : row.variant();
        if ("~".equals(row.carries())) {
            lines.add(Component.translatable("townstead.heritage.tooltip.carrier_wild", shown).withStyle(ChatFormatting.GRAY));
        } else if (!row.carries().isEmpty()) {
            lines.add(Component.translatable("townstead.heritage.tooltip.carrier", shown, row.carries()).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /**
     * Colourblind-safe qualitative palette (Okabe-Ito). Distinct under the common
     * forms of colour blindness, so the stacked ancestry bar reads for everyone; the
     * text legend below it carries the meaning regardless of colour.
     */
    private static final int[] ANCESTRY_PALETTE = {
            0xFF56B4E9, 0xFFE69F00, 0xFF009E73, 0xFFCC79A7,
            0xFF0072B2, 0xFFD55E00, 0xFFF0E442, 0xFF999999,
    };

    /** A stable palette colour for an ancestry (same name → same colour, no authoring needed). */
    private static int ancestryColor(String name) {
        int h = name == null ? 0 : name.hashCode();
        return ANCESTRY_PALETTE[Math.floorMod(h, ANCESTRY_PALETTE.length)];
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
