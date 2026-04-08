package com.aetherianartificer.townstead.client.gui.dialogue;

import net.conczin.mca.resources.data.dialogue.Question;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Right-side panel showing dialogue choices.
 * Supports mouse hover and keyboard navigation.
 */
public class ChoicePanel {
    private static final int BG_COLOR = 0xAA000000;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int BORDER_HIGHLIGHT = 0xFF888888;
    private static final int NORMAL_COLOR = 0xAAFFFFFF;
    private static final int HOVER_COLOR = 0xFFD7D784;
    private static final int SELECTED_BG = 0x44FFFFFF;
    private static final int PADDING = 8;
    private static final int ENTRY_HEIGHT = 16;
    private static final int INDICATOR_WIDTH = 10;

    private List<String> choices = List.of();
    private String questionId = "";
    private boolean visible;
    private int hoveredIndex = -1;
    private int selectedIndex = 0;

    private int x, y, width, height;

    public void layout(int screenWidth, int screenHeight, int dialogueBoxY) {
        this.width = (int) (screenWidth * 0.30);
        this.x = screenWidth - this.width - 20;
        updateHeight();
        this.y = dialogueBoxY - this.height - 8;
    }

    public void setChoices(String questionId, List<String> choices) {
        this.questionId = questionId;
        this.choices = choices;
        this.hoveredIndex = -1;
        this.selectedIndex = 0;
        updateHeight();
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible) {
            selectedIndex = 0;
            hoveredIndex = -1;
        }
    }

    public boolean isVisible() {
        return visible && !choices.isEmpty();
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!isVisible()) return;

        // Background
        graphics.fill(x, y, x + width, y + height, BG_COLOR);

        // Border
        graphics.fill(x, y, x + width, y + 1, BORDER_HIGHLIGHT);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER_COLOR);
        graphics.fill(x, y, x + 1, y + height, BORDER_HIGHLIGHT);
        graphics.fill(x + width - 1, y, x + width, y + height, BORDER_COLOR);

        // Choices
        int entryY = y + PADDING;
        hoveredIndex = -1;
        for (int i = 0; i < choices.size(); i++) {
            boolean mouseHover = mouseX >= x && mouseX <= x + width
                    && mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT;
            boolean highlighted = mouseHover || i == selectedIndex;

            if (mouseHover) {
                hoveredIndex = i;
                selectedIndex = i;
            }

            if (highlighted) {
                graphics.fill(x + 2, entryY, x + width - 2, entryY + ENTRY_HEIGHT, SELECTED_BG);
            }

            String translationKey = Question.getTranslationKey(questionId, choices.get(i));
            Component text = Component.translatable(translationKey);
            int textColor = highlighted ? HOVER_COLOR : NORMAL_COLOR;

            // Selection indicator
            if (highlighted) {
                graphics.drawString(font, "\u25B8", x + PADDING, entryY + 4, HOVER_COLOR);
            }

            graphics.drawString(font, text, x + PADDING + INDICATOR_WIDTH, entryY + 4, textColor);
            entryY += ENTRY_HEIGHT;
        }
    }

    public void moveSelection(int delta) {
        if (choices.isEmpty()) return;
        selectedIndex = Math.floorMod(selectedIndex + delta, choices.size());
    }

    /**
     * @return the selected choice string, or null if nothing selected
     */
    public String getSelectedChoice() {
        if (choices.isEmpty() || selectedIndex < 0 || selectedIndex >= choices.size()) {
            return null;
        }
        return choices.get(selectedIndex);
    }

    /**
     * @return the choice under the mouse, or null
     */
    public String getHoveredChoice() {
        if (hoveredIndex < 0 || hoveredIndex >= choices.size()) {
            return null;
        }
        return choices.get(hoveredIndex);
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!isVisible()) return false;
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return false;
        return hoveredIndex >= 0;
    }

    public boolean isEmpty() {
        return choices.isEmpty();
    }

    private void updateHeight() {
        this.height = PADDING * 2 + Math.max(1, choices.size()) * ENTRY_HEIGHT;
    }
}
