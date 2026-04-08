package com.aetherianartificer.townstead.client.gui.dialogue;

import net.conczin.mca.resources.data.dialogue.Question;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Right-side panel showing dialogue choices.
 * Bottom-aligned above the dialogue box, grows upward.
 * Scrollable if choices exceed available screen height.
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
    private static final int LINE_HEIGHT = 11;
    private static final int ENTRY_SPACING = 6;
    private static final int INDICATOR_WIDTH = 10;
    private static final int GAP_ABOVE_DIALOGUE = 8;
    private static final int MIN_TOP_MARGIN = 10;

    private List<String> choices = List.of();
    private String questionId = "";
    private boolean visible;
    private int hoveredIndex = -1;
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    // Layout anchors (set by layout())
    private int panelWidth;
    private int panelX;
    private int bottomY; // bottom edge of the panel (fixed)
    private int maxHeight; // maximum height before scrolling kicks in

    // Computed per-choice data
    private final List<List<FormattedCharSequence>> wrappedChoices = new ArrayList<>();
    private final List<Integer> entryHeights = new ArrayList<>();
    private int contentHeight; // total height of all entries + spacing
    private boolean needsScroll;

    // Actual render bounds
    private int x, y, width, height;

    public void layout(int screenWidth, int screenHeight, int dialogueBoxY) {
        this.panelWidth = (int) (screenWidth * 0.30);
        this.panelX = screenWidth - this.panelWidth - 20;
        this.bottomY = dialogueBoxY - GAP_ABOVE_DIALOGUE;
        this.maxHeight = bottomY - MIN_TOP_MARGIN;
        recomputeBounds();
    }

    public void setChoices(String questionId, List<String> choices, Font font) {
        this.questionId = questionId;
        this.choices = choices;
        this.hoveredIndex = -1;
        this.selectedIndex = 0;
        this.scrollOffset = 0;
        wrapChoices(font);
        recomputeBounds();
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible) {
            selectedIndex = 0;
            hoveredIndex = -1;
            scrollOffset = 0;
        }
    }

    public boolean isVisible() {
        return visible && !choices.isEmpty();
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!isVisible()) return;

        // Enable scissor to clip scrolled content
        graphics.enableScissor(x, y, x + width, y + height);

        // Background
        graphics.fill(x, y, x + width, y + height, BG_COLOR);

        // Choices
        int entryY = y + PADDING - scrollOffset;
        hoveredIndex = -1;
        for (int i = 0; i < choices.size(); i++) {
            int entryH = entryHeights.get(i);
            int entryBottom = entryY + entryH;

            // Only process visible entries
            if (entryBottom > y && entryY < y + height) {
                boolean mouseHover = mouseX >= x && mouseX <= x + width
                        && mouseY >= Math.max(entryY, y) && mouseY < Math.min(entryBottom, y + height);
                boolean highlighted = mouseHover || i == selectedIndex;

                if (mouseHover) {
                    hoveredIndex = i;
                    selectedIndex = i;
                }

                if (highlighted) {
                    graphics.fill(x + 2, entryY - 1, x + width - 2, entryY + entryH + 1, SELECTED_BG);
                }

                int textColor = highlighted ? HOVER_COLOR : NORMAL_COLOR;

                if (highlighted) {
                    int indicatorY = entryY + (entryH - LINE_HEIGHT) / 2;
                    graphics.drawString(font, "\u25B8", x + PADDING, indicatorY, HOVER_COLOR);
                }

                List<FormattedCharSequence> lines = wrappedChoices.get(i);
                int lineY = entryY;
                for (FormattedCharSequence line : lines) {
                    graphics.drawString(font, line, x + PADDING + INDICATOR_WIDTH, lineY, textColor);
                    lineY += LINE_HEIGHT;
                }
            }

            entryY += entryH + ENTRY_SPACING;
        }

        graphics.disableScissor();

        // Border (drawn after scissor so it's always fully visible)
        graphics.fill(x, y, x + width, y + 1, BORDER_HIGHLIGHT);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER_COLOR);
        graphics.fill(x, y, x + 1, y + height, BORDER_HIGHLIGHT);
        graphics.fill(x + width - 1, y, x + width, y + height, BORDER_COLOR);

        // Scroll indicators
        if (needsScroll) {
            if (scrollOffset > 0) {
                graphics.drawCenteredString(font, "\u25B2", x + width / 2, y + 2, 0x88FFFFFF);
            }
            if (scrollOffset < contentHeight - (height - PADDING * 2)) {
                graphics.drawCenteredString(font, "\u25BC", x + width / 2, y + height - 10, 0x88FFFFFF);
            }
        }
    }

    public boolean mouseScrolled(double delta) {
        if (!isVisible() || !needsScroll) return false;
        int maxScroll = contentHeight - (height - PADDING * 2);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) (delta * LINE_HEIGHT * 2), maxScroll));
        return true;
    }

    public void moveSelection(int delta) {
        if (choices.isEmpty()) return;
        selectedIndex = Math.floorMod(selectedIndex + delta, choices.size());
        ensureSelectedVisible();
    }

    public String getSelectedChoice() {
        if (choices.isEmpty() || selectedIndex < 0 || selectedIndex >= choices.size()) {
            return null;
        }
        return choices.get(selectedIndex);
    }

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

    private void wrapChoices(Font font) {
        wrappedChoices.clear();
        entryHeights.clear();
        int maxTextWidth = panelWidth - PADDING * 2 - INDICATOR_WIDTH;
        for (String choice : choices) {
            String translationKey = Question.getTranslationKey(questionId, choice);
            Component text = Component.translatable(translationKey);
            List<FormattedCharSequence> lines = font.split(text, maxTextWidth);
            wrappedChoices.add(lines);
            entryHeights.add(Math.max(1, lines.size()) * LINE_HEIGHT);
        }

        contentHeight = 0;
        for (int i = 0; i < entryHeights.size(); i++) {
            contentHeight += entryHeights.get(i);
            if (i < entryHeights.size() - 1) {
                contentHeight += ENTRY_SPACING;
            }
        }
    }

    private void recomputeBounds() {
        this.width = panelWidth;
        this.x = panelX;

        int desiredHeight = contentHeight + PADDING * 2;
        if (desiredHeight > maxHeight) {
            this.height = maxHeight;
            this.needsScroll = true;
        } else {
            this.height = desiredHeight;
            this.needsScroll = false;
        }

        // Bottom-aligned: panel bottom sits at bottomY
        this.y = bottomY - this.height;
    }

    private void ensureSelectedVisible() {
        if (!needsScroll || entryHeights.isEmpty()) return;

        // Calculate the top of the selected entry relative to content
        int entryTop = 0;
        for (int i = 0; i < selectedIndex; i++) {
            entryTop += entryHeights.get(i) + ENTRY_SPACING;
        }
        int entryBottom = entryTop + entryHeights.get(selectedIndex);
        int visibleHeight = height - PADDING * 2;

        if (entryTop < scrollOffset) {
            scrollOffset = entryTop;
        } else if (entryBottom > scrollOffset + visibleHeight) {
            scrollOffset = entryBottom - visibleHeight;
        }
    }
}
