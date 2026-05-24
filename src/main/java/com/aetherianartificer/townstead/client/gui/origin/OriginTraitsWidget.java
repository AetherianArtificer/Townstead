package com.aetherianartificer.townstead.client.gui.origin;

import com.aetherianartificer.townstead.client.origin.OriginCatalogClient;
import com.aetherianartificer.townstead.origin.GeneCatalogEntry;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The traits box: the origin's inherited genes as RimWorld-style text chips,
 * grouped by category, hoverable for a tooltip (dominance, occurrence/range/
 * influence, locus). Shows "No distinctive traits" when empty. Scrolls
 * independently of the description box.
 */
public class OriginTraitsWidget extends ScrollPane {

    private static final int CHIP_H = 12;

    @Nullable private OriginCatalogEntry origin;
    private final List<ChipHit> chipHits = new ArrayList<>();

    public OriginTraitsWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public void setOrigin(@Nullable OriginCatalogEntry origin) {
        this.origin = origin;
        resetScroll();
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

        g.drawString(mc.font, Component.translatable("townstead.origin.inherited_genes"), left, cy, 0xFFB89A6A, false);
        cy += 11;
        if (origin.inheritedGenes().isEmpty()) {
            g.drawString(mc.font, Component.translatable("townstead.origin.no_traits"), left, cy, 0xFF707070, false);
            cy += 11;
            return cy - top;
        }

        Map<String, List<ChipData>> byCategory = new LinkedHashMap<>();
        for (OriginCatalogEntry.Inherited in : origin.inheritedGenes()) {
            GeneCatalogEntry gene = OriginCatalogClient.gene(in.geneId());
            if (gene == null) continue;
            byCategory.computeIfAbsent(gene.category(), k -> new ArrayList<>())
                    .add(new ChipData(gene, in.occurrence()));
        }
        for (Map.Entry<String, List<ChipData>> cat : byCategory.entrySet()) {
            g.drawString(mc.font, cat.getKey(), left, cy, 0xFF7FA7C9, false);
            cy += 10;
            int chipX = left;
            for (ChipData data : cat.getValue()) {
                String label = data.gene().name();
                int chipW = mc.font.width(label) + 8;
                if (chipX + chipW > left + innerW && chipX > left) {
                    chipX = left;
                    cy += CHIP_H + 3;
                }
                boolean hovered = mouseX >= chipX && mouseX < chipX + chipW
                        && mouseY >= cy && mouseY < cy + CHIP_H
                        && mouseY >= innerTop && mouseY < innerBottom;
                drawChip(g, mc, data.gene(), label, chipX, cy, chipW, hovered);
                chipHits.add(new ChipHit(chipX, cy, chipW, CHIP_H, data.gene(), data.occurrence()));
                chipX += chipW + 3;
            }
            cy += CHIP_H + 5;
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

    private void drawChip(GuiGraphics g, Minecraft mc, GeneCatalogEntry gene, String label,
                          int cx, int cy, int cw, boolean hovered) {
        g.fill(cx, cy, cx + cw, cy + CHIP_H, hovered ? 0xFF5C5C5C : 0xFF3C3C3C);
        g.fill(cx, cy, cx + cw, cy + 1, 0xFF1A1A1A);
        g.fill(cx, cy + CHIP_H - 1, cx + cw, cy + CHIP_H, 0xFF1A1A1A);
        g.fill(cx, cy, cx + 1, cy + CHIP_H, 0xFF1A1A1A);
        g.fill(cx + cw - 1, cy, cx + cw, cy + CHIP_H, 0xFF1A1A1A);
        int accent = gene.isInfluence() ? 0xFFC9A77F : (gene.isRecessive() ? 0xFF8A8A8A : 0xFFE0C060);
        g.fill(cx + 1, cy + 1, cx + 2, cy + CHIP_H - 1, accent);
        g.drawString(mc.font, label, cx + 4, cy + 2, 0xFFE6E6E6, false);
    }

    private List<Component> tooltipFor(ChipHit hit) {
        GeneCatalogEntry gene = hit.gene();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(gene.name()).withStyle(ChatFormatting.WHITE));
        if (!gene.description().isEmpty()) {
            lines.add(Component.literal(gene.description()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.literal(gene.isRecessive() ? "Recessive" : "Dominant")
                .withStyle(gene.isRecessive() ? ChatFormatting.GRAY : ChatFormatting.GOLD));
        if (gene.isInfluence()) {
            int pct = Math.round(gene.amount() * 100f);
            lines.add(Component.literal("Influences " + prettify(gene.targetId()) + ": "
                    + (pct >= 0 ? "+" : "") + pct + "%").withStyle(ChatFormatting.YELLOW));
        } else if (gene.isRange()) {
            lines.add(Component.literal("Range: " + fmt(gene.min()) + " - " + fmt(gene.max()))
                    .withStyle(ChatFormatting.GREEN));
        } else {
            lines.add(Component.literal("Occurrence: " + Math.round(hit.occurrence() * 100f) + "%")
                    .withStyle(ChatFormatting.GREEN));
        }
        if (!gene.alleleGroup().isEmpty()) {
            lines.add(Component.literal("Locus: " + shortId(gene.alleleGroup())).withStyle(ChatFormatting.AQUA));
        }
        return lines;
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String shortId(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private static String prettify(String id) {
        String s = shortId(id).replace('_', ' ');
        if (s.isEmpty()) return s;
        StringBuilder out = new StringBuilder();
        for (String word : s.split(" ")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
