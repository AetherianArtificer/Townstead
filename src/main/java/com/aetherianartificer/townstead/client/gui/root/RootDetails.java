package com.aetherianartificer.townstead.client.gui.root;

import com.aetherianartificer.townstead.client.gui.common.IconArt;
import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.RootBrowseEntry;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a root the same way wherever it is described: the Root tab's summary and the root
 * browser's preview. Each part returns the y it ended on, so callers stack them.
 */
final class RootDetails {

    static final int NAME = 0xFFFFFFFF;
    static final int GOLD = 0xFFB89A6A;
    static final int TEXT = 0xFFA8A8A8;
    static final int DIM = 0xFF7A7A7A;
    static final int ACTIVE = 0xFFFFE08A;
    static final int STRENGTH = 0xFF9BE08A;
    static final int DRAWBACK = 0xFFF08A7A;
    static final int WELL = 0x73000000;

    private RootDetails() {}

    /** Icon (when the root has one), name, and "Species > Ancestry · Pack". */
    static int header(GuiGraphics g, RootCatalogEntry root, @Nullable RootBrowseEntry browse, int x, int y, int w) {
        Font font = Minecraft.getInstance().font;
        int textX = x;
        String icon = browse == null ? "" : browse.icon();
        if (!icon.isEmpty() && IconArt.draw(g, icon, x, y + 1, 1f)) textX = x + 20;
        int textW = w - (textX - x);
        g.drawString(font, trim(font, root.name(), textW), textX, y, NAME, false);
        g.drawString(font, trim(font, crumb(root, browse), textW), textX, y + 10, GOLD, false);
        return y + 20;
    }

    static String crumb(RootCatalogEntry root, @Nullable RootBrowseEntry browse) {
        StringBuilder out = new StringBuilder();
        if (!root.speciesName().isEmpty()) out.append(root.speciesName());
        if (!root.ancestryName().isEmpty() && !root.ancestryName().equals(root.speciesName())) {
            if (!out.isEmpty()) out.append(" > ");
            out.append(root.ancestryName());
        }
        if (browse != null) {
            String pack = text(browse.packQualifier()).getString();
            if (!pack.isEmpty()) {
                if (!out.isEmpty()) out.append(" · ");
                out.append(pack);
            }
        }
        return out.toString();
    }

    /** The backstory, wrapped, at most {@code maxLines} lines. */
    static int backstory(GuiGraphics g, RootCatalogEntry root, int x, int y, int w, int maxLines) {
        if (root.backstory().isEmpty()) return y;
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = font.split(Component.literal(root.backstory()), w);
        int n = Math.min(maxLines, lines.size());
        for (int i = 0; i < n; i++) {
            g.drawString(font, lines.get(i), x, y, TEXT, false);
            y += 10;
        }
        return y;
    }

    /** Inner padding of a recessed box, matching the panels around it. */
    static final int INSET = 4;
    static final int LINE = 10;

    /**
     * The facts as a definition list in a recessed box the full width given: labels in one column,
     * values aligned in a second, a long value wrapping within its column.
     */
    static int facts(GuiGraphics g, @Nullable RootBrowseEntry browse, int x, int y, int w) {
        if (browse == null || browse.facts().isEmpty()) return y;
        Font font = Minecraft.getInstance().font;
        int labelW = 0;
        for (RootBrowseEntry.Fact fact : browse.facts()) {
            labelW = Math.max(labelW, font.width(Component.translatable("townstead.root.fact." + fact.kind())));
        }
        int valueX = x + INSET + labelW + 6;
        int valueW = Math.max(20, x + w - INSET - valueX);
        List<List<FormattedCharSequence>> values = new ArrayList<>();
        int lines = 0;
        for (RootBrowseEntry.Fact fact : browse.facts()) {
            List<FormattedCharSequence> wrapped = font.split(text(fact.value()), valueW);
            values.add(wrapped);
            lines += Math.max(1, wrapped.size());
        }
        int h = lines * LINE + INSET * 2 - 2;
        g.fill(x, y, x + w, y + h, WELL);
        int line = y + INSET;
        for (int i = 0; i < browse.facts().size(); i++) {
            RootBrowseEntry.Fact fact = browse.facts().get(i);
            g.drawString(font, Component.translatable("townstead.root.fact." + fact.kind()), x + INSET, line, DIM, false);
            for (FormattedCharSequence part : values.get(i)) {
                g.drawString(font, part, valueX, line, 0xFFE0E0E0, false);
                line += LINE;
            }
            if (values.get(i).isEmpty()) line += LINE;
        }
        return y + h;
    }

    /** The powers, coloured by kind; records the hovered one's description for a tooltip. */
    static int powers(GuiGraphics g, @Nullable RootBrowseEntry browse, int x, int y, int w,
                      int mouseX, int mouseY, Hover hover) {
        if (browse == null) return y;
        Font font = Minecraft.getInstance().font;
        for (RootBrowseEntry.Power power : browse.powers()) {
            GeneCatalogEntry gene = RootCatalogClient.gene(power.geneId());
            String name = gene != null ? gene.name() : power.geneId();
            int color = switch (power.kind()) {
                case ACTIVE -> ACTIVE;
                case STRENGTH -> STRENGTH;
                case DRAWBACK -> DRAWBACK;
            };
            String shown = trim(font, name, w);
            g.drawString(font, shown, x, y, color, false);
            int end = x + font.width(shown);
            if (power.kind() == RootBrowseEntry.Power.Kind.ACTIVE && power.cooldownTicks() > 0) {
                Component cd = Component.translatable("townstead.root.power.cooldown",
                        Math.round(power.cooldownTicks() / 20f));
                if (end + 4 + font.width(cd) <= x + w) {
                    g.drawString(font, cd, end + 4, y, DIM, false);
                }
            }
            if (gene != null && !gene.description().isEmpty()
                    && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 10) {
                hover.set(Component.literal(name), Component.literal(gene.description()), color);
            }
            y += 10;
        }
        return y;
    }

    static Component text(RootBrowseEntry.Text text) {
        if (text.key().isEmpty()) return Component.literal(text.fallback());
        return Component.translatableWithFallback(text.key(), text.fallback(), text.args().toArray());
    }

    static String trim(Font font, String text, int width) {
        if (width <= 0) return "";
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    /** A power tooltip waiting to be drawn above everything else. */
    static final class Hover {
        private @Nullable Component title;
        private @Nullable Component body;
        private int color;

        void clear() {
            title = null;
            body = null;
        }

        void set(Component title, Component body, int color) {
            this.title = title;
            this.body = body;
            this.color = color;
        }

        void draw(GuiGraphics g, Font font, int mouseX, int mouseY) {
            if (title == null || body == null) return;
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(title.copy().withStyle(style -> style.withColor(color & 0xFFFFFF)).getVisualOrderText());
            lines.addAll(font.split(body, 200));
            g.renderTooltip(font, lines, mouseX, mouseY);
        }
    }
}
