package com.aetherianartificer.townstead.client.gui.dialogue;

import com.aetherianartificer.townstead.client.gui.dialogue.effect.EffectTagParser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Everything said in one conversation, newest last, shown as a scrollable overlay. */
public class DialogueLog {
    private static final int MAX_ENTRIES = 200;
    private static final int BG = 0xE8101010;
    private static final int BORDER_LIGHT = 0xFFA0A0A0;
    private static final int BORDER_DARK = 0xFF373737;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int NAME_COLOR = 0xFFFFD700;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int CHOICE_COLOR = 0xFF9FC5E8;
    private static final int PADDING = 10;
    private static final int LINE_HEIGHT = 11;
    private static final int ENTRY_GAP = 6;

    private record Entry(String name, String text, boolean choice) {}

    private final List<Entry> entries = new ArrayList<>();
    private boolean open;
    /** Pixels scrolled up from the newest line. */
    private int scroll;
    private int maxScroll;

    public void addLine(Component name, Component text) {
        String clean = EffectTagParser.stripTags(text.getString()).strip();
        if (clean.isEmpty()) return;
        String who = name == null ? "" : name.getString();
        if (!entries.isEmpty()) {
            Entry last = entries.get(entries.size() - 1);
            if (!last.choice() && last.name().equals(who) && last.text().equals(clean)) return;
        }
        add(new Entry(who, clean, false));
    }

    public void addChoice(Component label) {
        String clean = EffectTagParser.stripTags(label.getString()).strip();
        if (!clean.isEmpty()) add(new Entry("", clean, true));
    }

    private void add(Entry entry) {
        entries.add(entry);
        if (entries.size() > MAX_ENTRIES) entries.remove(0);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
        scroll = 0;
    }

    public void scroll(double delta) {
        scroll = Math.max(0, Math.min(maxScroll, scroll + (int) (delta * LINE_HEIGHT * 3)));
    }

    public void render(GuiGraphics g, Font font, int x, int top, int width, int bottom) {
        if (!open) return;
        g.fill(x, top, x + width, bottom, BG);
        g.fill(x, top, x + width, top + 1, BORDER_LIGHT);
        g.fill(x, top, x + 1, bottom, BORDER_LIGHT);
        g.fill(x, bottom - 1, x + width, bottom, BORDER_DARK);
        g.fill(x + width - 1, top, x + width, bottom, BORDER_DARK);

        Component title = Component.translatable("townstead.dialogue.log.title");
        g.drawString(font, title, x + PADDING, top + PADDING, TITLE_COLOR);

        int textWidth = width - PADDING * 2;
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (Entry entry : entries) {
            if (!lines.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY);
                colors.add(0);
            }
            if (entry.choice()) {
                for (FormattedCharSequence line : font.split(Component.literal("> " + entry.text()), textWidth)) {
                    lines.add(line);
                    colors.add(CHOICE_COLOR);
                }
                continue;
            }
            if (!entry.name().isEmpty()) {
                lines.add(Component.literal(entry.name()).getVisualOrderText());
                colors.add(NAME_COLOR);
            }
            for (FormattedCharSequence line : font.split(Component.literal(entry.text()), textWidth)) {
                lines.add(line);
                colors.add(TEXT_COLOR);
            }
        }

        int clipTop = top + PADDING + LINE_HEIGHT + ENTRY_GAP;
        int clipBottom = bottom - PADDING;
        int contentHeight = lines.size() * LINE_HEIGHT;
        maxScroll = Math.max(0, contentHeight - (clipBottom - clipTop));
        scroll = Math.min(scroll, maxScroll);

        g.enableScissor(x, clipTop, x + width, clipBottom);
        int y = clipBottom - contentHeight + scroll;
        for (int i = 0; i < lines.size(); i++) {
            if (y + LINE_HEIGHT > clipTop && y < clipBottom) {
                g.drawString(font, lines.get(i), x + PADDING, y, colors.get(i));
            }
            y += LINE_HEIGHT;
        }
        g.disableScissor();

        if (scroll < maxScroll) g.drawCenteredString(font, "▲", x + width / 2, clipTop - LINE_HEIGHT, 0x88FFFFFF);
        if (scroll > 0) g.drawCenteredString(font, "▼", x + width / 2, bottom - LINE_HEIGHT, 0x88FFFFFF);
    }
}
