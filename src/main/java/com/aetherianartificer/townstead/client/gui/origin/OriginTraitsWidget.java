package com.aetherianartificer.townstead.client.gui.origin;

import com.aetherianartificer.townstead.client.origin.OriginCatalogClient;
import com.aetherianartificer.townstead.client.skin.SkinBlend;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.origin.GeneCatalogEntry;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A scrollable list of an origin's inherited genes as full-width "button" chips
 * — {@code [ CATEGORY  Gene  value ]} with a category-tinted label, a solid
 * border (dominant) or dashed border (recessive), and a per-kind value slot
 * (range bar, colour swatch, occurrence %, influence delta). A chip is flagged
 * red if a single origin grants two alleles of the same locus (an authoring
 * error). Hovering a chip shows a full stat-block tooltip (the Genes view relies
 * on these rather than a separate detail pane).
 */
public class OriginTraitsWidget extends ScrollPane {

    private static final int CHIP_H = 16;
    private static final int GAP = 2;

    @Nullable private OriginCatalogEntry origin;
    private final List<ChipData> chips = new ArrayList<>();
    private final Map<String, Integer> locusCount = new HashMap<>();
    private final List<ChipHit> chipHits = new ArrayList<>();

    public OriginTraitsWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public void setOrigin(@Nullable OriginCatalogEntry origin) {
        this.origin = origin;
        chips.clear();
        locusCount.clear();
        resetScroll();
        if (origin == null) return;
        // Cluster by category (no header rows; the category rides inside each chip)
        // and tally each locus so a duplicate-allele collision can be flagged.
        Map<String, List<ChipData>> byCategory = new LinkedHashMap<>();
        for (OriginCatalogEntry.Inherited in : origin.inheritedGenes()) {
            GeneCatalogEntry gene = OriginCatalogClient.gene(in.geneId());
            if (gene == null) continue;
            // Hydration genes are inert without a thirst mod (no icon, no effect): hide them.
            if ("hydration".equalsIgnoreCase(gene.category()) && !ThirstBridgeResolver.isActive()) continue;
            byCategory.computeIfAbsent(gene.category(), k -> new ArrayList<>())
                    .add(new ChipData(gene, in.occurrence()));
            if (!gene.locus().isEmpty()) locusCount.merge(gene.locus(), 1, Integer::sum);
        }
        for (List<ChipData> list : byCategory.values()) chips.addAll(list);
    }

    private boolean conflict(GeneCatalogEntry gene) {
        return !gene.locus().isEmpty() && locusCount.getOrDefault(gene.locus(), 0) > 1;
    }

    private record ChipHit(int x, int y, int w, int h, GeneCatalogEntry gene, float occurrence) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record ChipData(GeneCatalogEntry gene, float occurrence) {}

    @Override
    protected int renderContent(GuiGraphics g, int left, int top, int innerW,
                                int innerTop, int innerBottom, int mouseX, int mouseY) {
        chipHits.clear();
        if (origin == null) return 0;
        Minecraft mc = Minecraft.getInstance();
        int cy = top;

        if (chips.isEmpty()) {
            g.drawString(mc.font, Component.translatable("townstead.origin.no_traits"), left, cy, 0xFF707070, false);
            return 11;
        }

        for (ChipData data : chips) {
            GeneCatalogEntry gene = data.gene();
            boolean conflict = conflict(gene);
            // Border stays category-tinted; the locus drives only the (deferred)
            // breeding resolution, not the chip colour, since a single origin
            // grants at most one allele per locus.
            int borderColor = GeneVisuals.categoryTint(gene.category());
            boolean hovered = mouseX >= left && mouseX < left + innerW
                    && mouseY >= cy && mouseY < cy + CHIP_H
                    && mouseY >= innerTop && mouseY < innerBottom;
            drawChip(g, mc, gene, data.occurrence(), left, cy, innerW, hovered, borderColor, conflict);
            chipHits.add(new ChipHit(left, cy, innerW, CHIP_H, gene, data.occurrence()));
            cy += CHIP_H + GAP;
        }
        return cy - top;
    }

    @Override
    protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, int innerTop, int innerBottom) {
        if (mouseY < innerTop || mouseY >= innerBottom) return;
        for (ChipHit hit : chipHits) {
            if (hit.contains(mouseX, mouseY)) {
                g.renderComponentTooltip(Minecraft.getInstance().font, tooltipFor(hit), mouseX, mouseY);
                break;
            }
        }
    }

    private void drawChip(GuiGraphics g, Minecraft mc, GeneCatalogEntry gene, float occurrence,
                          int left, int cy, int innerW, boolean hovered,
                          int borderColor, boolean conflict) {
        int x2 = left + innerW;
        int y2 = cy + CHIP_H;
        int frame = conflict ? 0xFFD05858 : borderColor;
        GeneVisuals.drawStoneButton(g, left, cy, x2, y2, frame, hovered);
        GeneVisuals.drawBorder(g, left, cy, x2, y2, frame, gene.isRecessive());

        int textY = cy + (CHIP_H - 8) / 2;
        int contentX = left + 5;
        String cat = gene.category();
        int nameX;
        // Categories with an icon (Diet, Vitality, Activity, Appearance, and Hydration when a
        // thirst mod is loaded) render the icon in place of the category label.
        if (GeneVisuals.hasCategoryIcon(cat)) {
            GeneVisuals.drawCategoryIcon(g, cat, contentX, cy + (CHIP_H - GeneVisuals.ICON_SIZE) / 2);
            nameX = contentX + GeneVisuals.ICON_SIZE + 5;
        } else {
            g.drawString(mc.font, cat, contentX, textY, GeneVisuals.categoryTint(cat), false);
            nameX = contentX + mc.font.width(cat) + 6;
        }

        int valueLeft = renderValue(g, mc, gene, occurrence, x2 - 5, cy);
        int nameMaxW = valueLeft - 4 - nameX;
        String name = nameMaxW > 8 ? GeneVisuals.truncate(mc.font, gene.name(), nameMaxW) : gene.name();
        g.drawString(mc.font, name, nameX, textY, hovered ? 0xFFFFFFFF : 0xFFE6E6E6, false);
    }

    /** Draws the right-aligned value slot; returns its left edge so the name can be clipped to fit. */
    private int renderValue(GuiGraphics g, Minecraft mc, GeneCatalogEntry gene, float occ, int right, int cy) {
        if (gene.isColor()) {
            int w = 34, h = 6;
            int sx = right - w;
            // Preview the race's actual skin range: a light→dark slice of vanilla skin run through
            // this gene's exact tint + blend + strength (white tint → the plain human gradient).
            int packed = SkinBlend.pack(gene.colorFrom(), gene.blendMode(), gene.blendStrength());
            int light = SkinBlend.blend(0xF1E4C9, packed);
            int dark = SkinBlend.blend(0x3A2418, packed);
            GeneVisuals.drawSwatch(g, light, dark, sx, cy + (CHIP_H - h) / 2, w, h);
            return sx - 1;
        }
        if (gene.isRange()) {
            int w = 30, h = 4;
            int sx = right - w;
            GeneVisuals.drawRangeBar(g, gene.min(), gene.max(), sx, cy + (CHIP_H - h) / 2, w, h,
                    GeneVisuals.categoryTint(gene.category()));
            return sx - 1;
        }
        if (gene.isInfluence()) {
            int pct = Math.round(gene.amount() * 100f);
            String t = (pct >= 0 ? "+" : "") + pct + "%";
            int tw = mc.font.width(t);
            g.drawString(mc.font, t, right - tw, cy + (CHIP_H - 8) / 2, 0xFFC9A77F, false);
            return right - tw - 1;
        }
        int pct = Math.round(occ * 100f);
        if (pct < 100) {
            String t = pct + "%";
            int tw = mc.font.width(t);
            g.drawString(mc.font, t, right - tw, cy + (CHIP_H - 8) / 2, 0xFF7FC98A, false);
            return right - tw - 1;
        }
        return right;
    }

    private List<Component> tooltipFor(ChipHit hit) {
        GeneCatalogEntry gene = hit.gene();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(gene.name()).withStyle(ChatFormatting.WHITE));
        if (!gene.description().isEmpty()) {
            lines.add(Component.literal(gene.description()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.empty());
        lines.add(stat("Category", gene.category()));
        lines.add(Component.literal("Dominance: ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(gene.isRecessive() ? "Recessive" : "Dominant")
                        .withStyle(gene.isRecessive() ? ChatFormatting.GRAY : ChatFormatting.GOLD)));
        if (gene.isInfluence()) {
            int pct = Math.round(gene.amount() * 100f);
            lines.add(Component.literal("Effect: ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(GeneVisuals.prettify(gene.targetId()) + " " + (pct >= 0 ? "+" : "") + pct + "%")
                            .withStyle(ChatFormatting.YELLOW)));
        } else if (Math.round(hit.occurrence() * 100f) < 100) {
            lines.add(stat("Occurrence", Math.round(hit.occurrence() * 100f) + "%"));
        }
        return lines;
    }

    private static Component stat(String label, String value) {
        return Component.literal(label + ": ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.GRAY));
    }
}
