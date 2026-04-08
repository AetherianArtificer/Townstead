package com.aetherianartificer.townstead.client.gui.dialogue;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Bordered RPG-style panel at the bottom of the screen.
 * Displays the villager's name, typewriter-animated dialogue text,
 * and a blinking indicator when text is fully revealed.
 */
public class DialogueBox {
    private static final int BG_COLOR = 0xCC000000;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int BORDER_HIGHLIGHT = 0xFF888888;
    private static final int NAME_COLOR = 0xFFFFD700;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int INDICATOR_COLOR = 0xFFFFFFFF;
    private static final int PADDING = 8;
    private static final int NAME_HEIGHT = 12;
    private static final int LINE_HEIGHT = 11;

    private final TypewriterText typewriter = new TypewriterText();
    private Component villagerName = Component.empty();

    private int x, y, width, height;

    private static final int MARGIN = 20;

    public void layout(int screenWidth, int screenHeight) {
        this.x = MARGIN;
        this.width = screenWidth - MARGIN * 2;
        this.height = (int) (screenHeight * 0.20);
        this.y = screenHeight - this.height - 10;
    }

    public void setVillagerName(Component name) {
        this.villagerName = name;
    }

    public void setText(Component text, Font font) {
        int textWidth = width - PADDING * 2;
        typewriter.setText(text, font, textWidth);
    }

    public void tick() {
        typewriter.tick();
    }

    public void render(GuiGraphics graphics, Font font) {
        // Background
        graphics.fill(x, y, x + width, y + height, BG_COLOR);

        // Border - outer highlight then inner
        graphics.fill(x, y, x + width, y + 1, BORDER_HIGHLIGHT);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER_COLOR);
        graphics.fill(x, y, x + 1, y + height, BORDER_HIGHLIGHT);
        graphics.fill(x + width - 1, y, x + width, y + height, BORDER_COLOR);

        // Inner border for depth
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, BORDER_COLOR);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, BORDER_COLOR);

        // Villager name
        graphics.drawString(font, villagerName, x + PADDING, y + PADDING, NAME_COLOR);

        // Separator line below name
        int sepY = y + PADDING + NAME_HEIGHT;
        graphics.fill(x + PADDING, sepY, x + width - PADDING, sepY + 1, BORDER_COLOR);

        // Dialogue text (typewriter)
        int textY = sepY + 4;
        List<FormattedCharSequence> lines = typewriter.getRevealedLines();
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x + PADDING, textY, TEXT_COLOR);
            textY += LINE_HEIGHT;
        }

        // Blinking indicator when text is complete
        if (typewriter.shouldShowIndicator()) {
            String indicator = "\u25B6"; // right-pointing triangle
            graphics.drawString(font, indicator, x + width - PADDING - font.width(indicator), y + height - PADDING - 9, INDICATOR_COLOR);
        }
    }

    public TypewriterText getTypewriter() {
        return typewriter;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
