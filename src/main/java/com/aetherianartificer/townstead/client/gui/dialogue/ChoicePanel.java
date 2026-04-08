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
 * Supports hub mode for the organized main menu.
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
    private static final int HIGHLIGHT_PAD = 3;
    private static final int INDICATOR_WIDTH = 10;
    private static final int GAP_ABOVE_DIALOGUE = 8;
    private static final int MIN_TOP_MARGIN = 10;
    private static final int BACK_COLOR = 0xFF8888AA;

    // Raw MCA data (kept for sending packets)
    private List<String> rawAnswers = List.of();
    private String questionId = "";

    // Display entries — either from hub mode or raw mode
    private List<DisplayEntry> displayEntries = List.of();

    private boolean visible;
    private int hoveredIndex = -1;
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    // Hub mode state
    private boolean hubMode;
    private String currentSubMenu; // null = top level
    private List<DialogueMenuOrganizer.HubEntry> hubEntries = List.of();

    // Layout anchors
    private int panelWidth, panelX, bottomY, maxHeight;

    // Computed per-entry data
    private final List<List<FormattedCharSequence>> wrappedEntries = new ArrayList<>();
    private final List<Integer> entryHeights = new ArrayList<>();
    private int contentHeight;
    private boolean needsScroll;
    private int x, y, width, height;

    /** What a display entry represents. */
    record DisplayEntry(Component text, String mcaAnswer, String subMenuId, boolean isBack) {
        static DisplayEntry fromHub(DialogueMenuOrganizer.HubEntry hub) {
            return new DisplayEntry(hub.displayText(), hub.mcaAnswer(), hub.subMenuId(), false);
        }
        static DisplayEntry back() {
            return new DisplayEntry(Component.translatable("townstead.dialogue.back"), null, null, true);
        }
        static DisplayEntry raw(String questionId, String answer) {
            String rpgKey = DialogueMenuOrganizer.getRpgPhrasing(questionId, answer);
            String key = rpgKey != null ? rpgKey : Question.getTranslationKey(questionId, answer);
            return new DisplayEntry(Component.translatable(key), answer, null, false);
        }
        boolean isHub() { return subMenuId != null; }
        boolean isLeaf() { return mcaAnswer != null; }
    }

    public void layout(int screenWidth, int screenHeight, int dialogueBoxY) {
        this.panelWidth = (int) (screenWidth * 0.30);
        this.panelX = screenWidth - this.panelWidth - 20;
        this.bottomY = dialogueBoxY - GAP_ABOVE_DIALOGUE;
        this.maxHeight = bottomY - MIN_TOP_MARGIN;
        recomputeBounds();
    }

    public void setChoices(String questionId, List<String> choices, Font font) {
        this.questionId = questionId;
        this.rawAnswers = choices;
        this.hoveredIndex = -1;
        this.selectedIndex = 0;
        this.scrollOffset = 0;
        this.currentSubMenu = null;

        if (DialogueMenuOrganizer.isMainQuestion(questionId)) {
            this.hubMode = true;
            this.hubEntries = DialogueMenuOrganizer.buildTopLevel(choices);
            buildDisplayFromHub(font);
        } else {
            this.hubMode = false;
            buildDisplayFromRaw(font);
        }
        recomputeBounds();
    }

    private float fadeAlpha = 0f;
    private int fadeTick = 0;
    private static final int FADE_TICKS = 5;

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible) {
            selectedIndex = 0;
            hoveredIndex = -1;
            scrollOffset = 0;
            fadeTick = 0;
            fadeAlpha = 0f;
        }
    }

    public void tick() {
        if (visible && fadeAlpha < 1f) {
            fadeTick++;
            fadeAlpha = Math.min(1f, (float) fadeTick / FADE_TICKS);
        }
    }

    public boolean isVisible() {
        return visible && !displayEntries.isEmpty();
    }

    private static int aa(int argb, float alpha) {
        int origA = (argb >> 24) & 0xFF;
        return ((int) (origA * alpha) << 24) | (argb & 0x00FFFFFF);
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!isVisible() || fadeAlpha <= 0.01f) return;
        float a = fadeAlpha;

        graphics.enableScissor(x, y, x + width, y + height);
        float bgOpacity = DialogueAccessibility.backgroundAlpha();
        int bgAlphaInt = Math.min((int)(bgOpacity * 2f * 0xAA), 0xFF);
        int bgColor = (bgAlphaInt << 24) | (BG_COLOR & 0x00FFFFFF);
        graphics.fill(x, y, x + width, y + height, aa(bgColor, a));

        int entryY = y + PADDING - scrollOffset;
        hoveredIndex = -1;
        for (int i = 0; i < displayEntries.size(); i++) {
            int entryH = entryHeights.get(i);
            int entryBottom = entryY + entryH;

            if (entryBottom > y && entryY < y + height) {
                boolean mouseHover = mouseX >= x && mouseX <= x + width
                        && mouseY >= Math.max(entryY, y) && mouseY < Math.min(entryBottom, y + height);
                boolean highlighted = mouseHover || i == selectedIndex;

                if (mouseHover) {
                    hoveredIndex = i;
                    selectedIndex = i;
                }

                DisplayEntry entry = displayEntries.get(i);

                if (highlighted) {
                    graphics.fill(x + 2, entryY - HIGHLIGHT_PAD, x + width - 2, entryY + entryH + HIGHLIGHT_PAD, SELECTED_BG);
                }

                int textColor;
                if (entry.isBack()) {
                    textColor = highlighted ? HOVER_COLOR : BACK_COLOR;
                } else {
                    textColor = highlighted ? HOVER_COLOR : NORMAL_COLOR;
                }

                if (highlighted) {
                    int indicatorY = entryY + (entryH - LINE_HEIGHT) / 2;
                    String indicator = "\u25B8";
                    graphics.drawString(font, indicator, x + PADDING, indicatorY, HOVER_COLOR);
                }

                List<FormattedCharSequence> lines = wrappedEntries.get(i);
                int lineY = entryY;
                for (FormattedCharSequence line : lines) {
                    graphics.drawString(font, line, x + PADDING + INDICATOR_WIDTH, lineY, textColor);
                    lineY += LINE_HEIGHT;
                }
            }

            entryY += entryH + ENTRY_SPACING;
        }

        graphics.disableScissor();

        // Border
        graphics.fill(x, y, x + width, y + 1, aa(BORDER_HIGHLIGHT, a));
        graphics.fill(x, y + height - 1, x + width, y + height, aa(BORDER_COLOR, a));
        graphics.fill(x, y, x + 1, y + height, aa(BORDER_HIGHLIGHT, a));
        graphics.fill(x + width - 1, y, x + width, y + height, aa(BORDER_COLOR, a));

        if (needsScroll) {
            if (scrollOffset > 0) {
                graphics.drawCenteredString(font, "\u25B2", x + width / 2, y + 2, 0x88FFFFFF);
            }
            if (scrollOffset < contentHeight - (height - PADDING * 2)) {
                graphics.drawCenteredString(font, "\u25BC", x + width / 2, y + height - 10, 0x88FFFFFF);
            }
        }
    }

    /**
     * Handle a selection (click or Enter). Returns the result.
     */
    public SelectionResult select() {
        if (selectedIndex < 0 || selectedIndex >= displayEntries.size()) {
            return SelectionResult.NONE;
        }
        DisplayEntry entry = displayEntries.get(selectedIndex);
        if (entry.isBack()) {
            return SelectionResult.BACK;
        }
        if (entry.isHub()) {
            return new SelectionResult(SelectionResult.Type.SUB_MENU, entry.subMenuId(), null);
        }
        return new SelectionResult(SelectionResult.Type.ANSWER, null, entry.mcaAnswer());
    }

    /**
     * Navigate into a sub-menu. Call this from the screen when a hub entry is selected.
     */
    public void openSubMenu(String subMenuId, Font font) {
        this.currentSubMenu = subMenuId;
        this.selectedIndex = 0;
        this.hoveredIndex = -1;
        this.scrollOffset = 0;

        List<DialogueMenuOrganizer.HubEntry> subEntries =
                DialogueMenuOrganizer.buildSubMenu(subMenuId, rawAnswers);
        this.hubEntries = subEntries;
        buildDisplayFromHub(font);
        recomputeBounds();
    }

    /**
     * Navigate back to the top-level hub.
     */
    public void goBack(Font font) {
        this.currentSubMenu = null;
        this.selectedIndex = 0;
        this.hoveredIndex = -1;
        this.scrollOffset = 0;

        this.hubEntries = DialogueMenuOrganizer.buildTopLevel(rawAnswers);
        buildDisplayFromHub(font);
        recomputeBounds();
    }

    public boolean mouseScrolled(double delta) {
        if (!isVisible() || !needsScroll) return false;
        int maxScroll = contentHeight - (height - PADDING * 2);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) (delta * LINE_HEIGHT * 2), maxScroll));
        return true;
    }

    public void moveSelection(int delta) {
        if (displayEntries.isEmpty()) return;
        selectedIndex = Math.floorMod(selectedIndex + delta, displayEntries.size());
        ensureSelectedVisible();
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!isVisible()) return false;
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return false;
        return hoveredIndex >= 0;
    }

    public boolean isEmpty() {
        return displayEntries.isEmpty();
    }

    // --- Internal ---

    private void buildDisplayFromHub(Font font) {
        displayEntries = new ArrayList<>();
        for (DialogueMenuOrganizer.HubEntry entry : hubEntries) {
            displayEntries.add(DisplayEntry.fromHub(entry));
        }
        if (currentSubMenu != null) {
            displayEntries.add(DisplayEntry.back());
        }
        wrapEntries(font);
    }

    private void buildDisplayFromRaw(Font font) {
        displayEntries = new ArrayList<>();
        for (String answer : rawAnswers) {
            displayEntries.add(DisplayEntry.raw(questionId, answer));
        }
        wrapEntries(font);
    }

    private void wrapEntries(Font font) {
        wrappedEntries.clear();
        entryHeights.clear();
        int maxTextWidth = panelWidth - PADDING * 2 - INDICATOR_WIDTH;
        for (DisplayEntry entry : displayEntries) {
            List<FormattedCharSequence> lines = font.split(entry.text(), maxTextWidth);
            wrappedEntries.add(lines);
            entryHeights.add(Math.max(1, lines.size()) * LINE_HEIGHT);
        }
        contentHeight = 0;
        for (int i = 0; i < entryHeights.size(); i++) {
            contentHeight += entryHeights.get(i);
            if (i < entryHeights.size() - 1) contentHeight += ENTRY_SPACING;
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
            this.height = Math.max(desiredHeight, PADDING * 2);
            this.needsScroll = false;
        }
        this.y = bottomY - this.height;
    }

    private void ensureSelectedVisible() {
        if (!needsScroll || entryHeights.isEmpty()) return;
        int entryTop = 0;
        for (int i = 0; i < selectedIndex; i++) {
            entryTop += entryHeights.get(i) + ENTRY_SPACING;
        }
        int entryBottom = entryTop + entryHeights.get(selectedIndex);
        int visibleHeight = height - PADDING * 2;
        if (entryTop < scrollOffset) scrollOffset = entryTop;
        else if (entryBottom > scrollOffset + visibleHeight) scrollOffset = entryBottom - visibleHeight;
    }

    /** Result of selecting an entry. */
    public record SelectionResult(Type type, String subMenuId, String mcaAnswer) {
        public enum Type { NONE, ANSWER, SUB_MENU, BACK }
        static final SelectionResult NONE = new SelectionResult(Type.NONE, null, null);
        static final SelectionResult BACK = new SelectionResult(Type.BACK, null, null);
    }
}
