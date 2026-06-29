package com.aetherianartificer.townstead.client.gui.character;

import com.aetherianartificer.townstead.client.accessibility.Accessibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Horizontally-scrollable Character-editor tab strip. When the tabs overflow the band it reserves a
 * dedicated ‹ / › arrow button at each end (greyed at the scroll limit, like MCA's own paginators) and
 * scrolls the tab area between them — wheel, arrow click, and auto-reveal of the current tab all work.
 * The tab buttons clip cleanly inside the inner band. Drawn with vanilla button sprites on 1.21 (fill
 * fallback on 1.20.1, which lacks the GUI sprite system); this feature only renders on the 1.21 MCA.
 */
public class CharacterTabStrip extends AbstractWidget {
    private static final int PAD = 8;
    private static final int GAP = 1;
    private static final int MIN_TAB = 30;
    private static final int ARROW = 12;        // width of each scroll-arrow button
    private static final int PADDLE_STEP = 56;  // px scrolled per arrow click
    // Fill-fallback colours for 1.20.1 (no GUI sprite system).
    private static final int BORDER = 0xFF101010;
    private static final int BG_NORMAL = 0xFF555555;
    private static final int BG_HOVER = 0xFF707070;
    private static final int BG_SELECTED = 0xFF2D2D2D;
    //? if >=1.21 {
    private static final net.minecraft.resources.ResourceLocation BTN =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("widget/button");
    private static final net.minecraft.resources.ResourceLocation BTN_DISABLED =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("widget/button_disabled");
    private static final net.minecraft.resources.ResourceLocation BTN_HIGHLIGHTED =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    //?}

    public record Entry(String pageId, Component label) {}

    private final List<Entry> entries;
    private final Consumer<String> onSelect;
    private final int[] tabX;     // content-space left of each tab
    private final int[] tabW;     // width of each tab
    private final int contentW;   // total laid-out width
    private String selected;
    private int scrollX;

    public CharacterTabStrip(int x, int y, int width, int height, List<Entry> entries,
                             String selected, Consumer<String> onSelect) {
        super(x, y, width, height, CommonComponents.EMPTY);
        this.entries = List.copyOf(entries);
        this.onSelect = onSelect;
        this.selected = selected;
        var font = Minecraft.getInstance().font;
        this.tabX = new int[this.entries.size()];
        this.tabW = new int[this.entries.size()];
        int cx = 0;
        for (int i = 0; i < this.entries.size(); i++) {
            int w = Math.max(MIN_TAB, font.width(this.entries.get(i).label()) + PAD * 2);
            tabX[i] = cx;
            tabW[i] = w;
            cx += w + GAP;
        }
        this.contentW = Math.max(0, cx - GAP);
        // Reveal the current tab if it lays out off-screen (e.g. a later tab is the open page).
        int sel = -1;
        for (int i = 0; i < this.entries.size(); i++) {
            if (this.entries.get(i).pageId().equals(selected)) { sel = i; break; }
        }
        if (sel >= 0) {
            int iw = innerWidth();
            this.scrollX = Math.max(0, Math.min(tabX[sel] - (iw - tabW[sel]) / 2, Math.max(0, contentW - iw)));
        }
    }

    public void setSelected(String pageId) { this.selected = pageId; }

    private boolean overflow() { return contentW > width; }
    private int innerX() { return getX() + (overflow() ? ARROW : 0); }
    private int innerWidth() { return width - (overflow() ? ARROW * 2 : 0); }
    private int maxScroll() { return Math.max(0, contentW - innerWidth()); }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        scrollX = Math.max(0, Math.min(scrollX, maxScroll()));
        var font = Minecraft.getInstance().font;
        int ix = innerX(), iw = innerWidth();
        g.enableScissor(ix, getY(), ix + iw, getY() + height);
        boolean inBand = mouseY >= getY() && mouseY < getY() + height && mouseX >= ix && mouseX < ix + iw;
        for (int i = 0; i < entries.size(); i++) {
            int sx = ix + tabX[i] - scrollX;
            int w = tabW[i];
            if (sx + w <= ix || sx >= ix + iw) continue; // fully clipped
            boolean isSel = entries.get(i).pageId().equals(selected);
            boolean hover = inBand && mouseX >= sx && mouseX < sx + w;
            drawButton(g, sx, w, isSel, hover);
            int color = isSel ? 0xFFA0A0A0 : 0xFFFFFFFF;
            g.drawCenteredString(font, entries.get(i).label(), sx + w / 2, getY() + (height - 8) / 2, color);
        }
        g.disableScissor();
        if (overflow()) {
            int h = getY();
            boolean lh = mouseY >= h && mouseY < h + height && mouseX >= getX() && mouseX < getX() + ARROW;
            boolean rh = mouseY >= h && mouseY < h + height && mouseX >= getX() + width - ARROW && mouseX < getX() + width;
            drawArrow(g, getX(), scrollX > 0, lh, "‹");
            drawArrow(g, getX() + width - ARROW, scrollX < maxScroll(), rh, "›");
        }
    }

    private void drawArrow(GuiGraphics g, int ax, boolean enabled, boolean hover, String glyph) {
        drawButton(g, ax, ARROW, !enabled, enabled && hover);
        int color = enabled ? 0xFFFFFFFF : 0xFF808080;
        g.drawCenteredString(Minecraft.getInstance().font, glyph, ax + ARROW / 2, getY() + (height - 8) / 2, color);
    }

    private void drawButton(GuiGraphics g, int sx, int w, boolean pressed, boolean hover) {
        //? if >=1.21 {
        net.minecraft.resources.ResourceLocation s = pressed ? BTN_DISABLED : (hover ? BTN_HIGHLIGHTED : BTN);
        g.blitSprite(s, sx, getY(), w, height);
        //?} else {
        /*int bg = pressed ? BG_SELECTED : (hover ? BG_HOVER : BG_NORMAL);
        g.fill(sx, getY(), sx + w, getY() + height, bg);
        g.fill(sx, getY(), sx + w, getY() + 1, BORDER);
        g.fill(sx, getY() + height - 1, sx + w, getY() + height, BORDER);
        g.fill(sx, getY(), sx + 1, getY() + height, BORDER);
        g.fill(sx + w - 1, getY(), sx + w, getY() + height, BORDER);
        *///?}
    }

    private boolean doScroll(double mouseX, double mouseY, double dy) {
        if (maxScroll() == 0) return false;
        if (!isMouseOver(mouseX, mouseY)) return false;
        int step = Accessibility.isReduceMotion() ? 40 : 24;
        scrollX = Math.max(0, Math.min(scrollX - (int) Math.signum(dy) * step, maxScroll()));
        return true;
    }

    //? if >=1.21 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        return doScroll(mouseX, mouseY, dy);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dy) {
        return doScroll(mouseX, mouseY, dy);
    }
    *///?}

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (overflow()) {
            if (mouseX < getX() + ARROW) {
                scrollX = Math.max(0, scrollX - PADDLE_STEP);
                return;
            }
            if (mouseX >= getX() + width - ARROW) {
                scrollX = Math.min(maxScroll(), scrollX + PADDLE_STEP);
                return;
            }
        }
        int ix = innerX(), iw = innerWidth();
        for (int i = 0; i < entries.size(); i++) {
            int sx = ix + tabX[i] - scrollX;
            int w = tabW[i];
            if (mouseX >= sx && mouseX < sx + w && mouseX >= ix && mouseX < ix + iw) {
                String id = entries.get(i).pageId();
                if (!id.equals(selected) && onSelect != null) onSelect.accept(id);
                return;
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.translatable("townstead.character.tabs"));
    }

    /** Convenience: build entries from resolved tabs. */
    public static List<Entry> entriesOf(CharacterEditorResolver.Resolved resolved) {
        List<Entry> out = new ArrayList<>();
        for (CharacterEditorResolver.Tab t : resolved.tabs()) out.add(new Entry(t.pageId(), t.label()));
        return out;
    }
}
