package com.aetherianartificer.townstead.client.gui.aspect;

import com.aetherianartificer.townstead.client.gui.common.Controls;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * The Aspects page's card for the aspect on show: its name, "Current" when they are it, what it
 * does, and its looks as {@code [<][ Coat 3 of 11 ][>]} rows. A locked page puts the reason on the
 * last line.
 */
public class AspectCard extends AbstractWidget {
    private static final int PAD = 6;
    private static final int ROW = Controls.SEG_H;
    private static final int ROW_GAP = 2;
    private static final int ARROW = 14;
    private static final int CURRENT_INK = 0xFFF2D36B;

    /** One look: its text, whether it can change now, and what to do with a step of -1 or +1. */
    public record Row(Component text, boolean enabled, IntConsumer step) {}

    private final Component name;
    private final boolean current;
    @Nullable private final Component description;
    private final List<Row> rows;
    @Nullable private final Component notice;

    public AspectCard(int x, int y, int width, int height, Component name, boolean current,
                      @Nullable Component description, List<Row> rows, @Nullable Component notice) {
        super(x, y, width, height, name);
        this.name = name;
        this.current = current;
        this.description = description;
        this.rows = List.copyOf(rows);
        this.notice = notice;
    }

    private int noticeTop() {
        return getY() + height - PAD - 8;
    }

    private int rowTop(int i) {
        int bottom = notice != null ? noticeTop() - 4 : getY() + height - PAD;
        return bottom - (rows.size() - i) * (ROW + ROW_GAP) + ROW_GAP;
    }

    private Controls.Rect prev(int i) {
        return new Controls.Rect(getX() + PAD, rowTop(i), ARROW, ROW);
    }

    private Controls.Rect next(int i) {
        return new Controls.Rect(getX() + width - PAD - ARROW, rowTop(i), ARROW, ROW);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int left = getX() + PAD;
        int right = getX() + width - PAD;
        g.fill(getX(), getY(), getX() + width, getY() + height, 0x50000000);

        int top = getY() + PAD;
        g.drawString(font, name, left, top, 0xFFFFFFFF, true);
        if (current) {
            Component tag = Component.translatable("townstead.aspect.current");
            g.drawString(font, tag, right - font.width(tag), top, CURRENT_INK, true);
        }
        top += font.lineHeight + 4;

        int textBottom = rows.isEmpty() ? (notice != null ? noticeTop() - 4 : getY() + height - PAD) : rowTop(0) - 4;
        if (description != null) {
            for (FormattedCharSequence line : font.split(description, right - left)) {
                if (top + font.lineHeight > textBottom) break;
                g.drawString(font, line, left, top, 0xFFDCDCDC, false);
                top += font.lineHeight + 1;
            }
        }

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            Controls.Rect p = prev(i), n = next(i);
            Controls.drawButton(g, font, p, "<", false, row.enabled() && p.contains(mouseX, mouseY), row.enabled());
            Controls.drawButton(g, font, n, ">", false, row.enabled() && n.contains(mouseX, mouseY), row.enabled());
            int plateL = p.right(), plateR = n.x();
            Palette.drawOutline(g, plateL, p.y(), plateR, p.bottom(), 0xFF000000);
            g.fill(plateL + 1, p.y() + 1, plateR - 1, p.bottom() - 1, row.enabled() ? 0xFF101010 : 0xFF1E1E1E);
            g.drawCenteredString(font, row.text(), (plateL + plateR) / 2, p.y() + (ROW - font.lineHeight) / 2 + 1,
                    row.enabled() ? 0xFFFFFFFF : 0xFFA0A0A0);
        }

        if (notice != null) {
            g.drawCenteredString(font, notice, getX() + width / 2, noticeTop(), 0xFFA0A0A0);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !active || button != 0) return false;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (!row.enabled()) continue;
            int step = prev(i).contains(mouseX, mouseY) ? -1 : next(i).contains(mouseX, mouseY) ? 1 : 0;
            if (step == 0) continue;
            playDownSound(Minecraft.getInstance().getSoundManager());
            row.step().accept(step);
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, name);
    }
}
