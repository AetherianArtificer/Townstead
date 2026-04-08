package com.aetherianartificer.townstead.client.gui.dialogue;

import com.aetherianartificer.townstead.client.gui.dialogue.DialogueAccessibility;
import com.aetherianartificer.townstead.client.gui.dialogue.effect.DialogueEffects;
import com.aetherianartificer.townstead.client.gui.dialogue.effect.EffectTagParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages character-by-character reveal of a {@link Component}, preserving formatting.
 * Supports inline effect tags, per-effect typewriter speed, and pagination.
 */
public class TypewriterText {
    private Component fullText = Component.empty();
    private List<FormattedCharSequence> fullLines = List.of();
    private int totalChars;
    private int revealedChars;
    private float revealAccumulator;
    private boolean complete;
    private int blinkTimer;
    private float baseSpeed = 1.0f;
    private boolean paused; // paused waiting for page advance

    // Pagination
    private int pageOffset; // first line index of current page
    private int maxVisibleLines = 100; // set by DialogueBox based on height

    // Parsed effect spans from inline tags
    private EffectTagParser.ParseResult effectParseResult;

    public void setText(Component text, Font font, int maxWidth) {
        String rawText = text.getString();

        if (rawText.contains("<") && rawText.contains(">")) {
            effectParseResult = EffectTagParser.parse(rawText);
            this.fullText = Component.literal(effectParseResult.cleanText());
        } else {
            effectParseResult = null;
            this.fullText = text;
        }

        // Wrap at full width — scissor clipping handles any scaled overflow
        this.fullLines = font.split(this.fullText, maxWidth);
        this.totalChars = countChars(fullLines);
        this.revealedChars = 0;
        this.revealAccumulator = 0;
        this.complete = false;
        this.paused = false;
        this.blinkTimer = 0;
        this.pageOffset = 0;
    }

    public void setMaxVisibleLines(int lines) {
        this.maxVisibleLines = lines;
    }

    public void tick() {
        if (complete || paused) {
            blinkTimer++;
            return;
        }

        float speed = baseSpeed;
        DialogueEffects charEffect = getEffectAt(revealedChars);
        if (charEffect != null) {
            speed *= charEffect.getTypewriterSpeed();
        }

        revealAccumulator += speed;
        int revealed = 0;
        while (revealAccumulator >= 1.0f && !complete && !paused) {
            revealAccumulator -= 1.0f;
            revealedChars++;
            revealed++;
            if (revealedChars >= totalChars) {
                complete = true;
            } else if (needsPageAdvance()) {
                paused = true;
            }
        }
        if (revealed > 0 && revealedChars % 3 == 0 && DialogueAccessibility.typewriterSoundEnabled()) {
            float pitch = 1.5f + (revealedChars % 7) * 0.05f;
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.WOODEN_BUTTON_CLICK_ON, pitch, 0.15f));
        }
    }

    /** Get the lines to display for the current page. */
    public List<FormattedCharSequence> getRevealedLines() {
        List<FormattedCharSequence> allRevealed;
        if (complete) {
            allRevealed = fullLines;
        } else {
            allRevealed = new ArrayList<>();
            int remaining = revealedChars;
            for (FormattedCharSequence line : fullLines) {
                int lineLen = charLength(line);
                if (remaining <= 0) break;
                if (remaining >= lineLen) {
                    allRevealed.add(line);
                    remaining -= lineLen;
                } else {
                    allRevealed.add(truncate(line, remaining));
                    break;
                }
            }
        }

        // Return only the current page's lines
        int end = Math.min(allRevealed.size(), pageOffset + maxVisibleLines);
        if (pageOffset >= allRevealed.size()) return List.of();
        return allRevealed.subList(pageOffset, end);
    }

    /** Check if revealed text has gone past the current page's visible area. */
    public boolean needsPageAdvance() {
        int revealedLineCount = countRevealedLines();
        return revealedLineCount > pageOffset + maxVisibleLines;
    }

    /** Whether the typewriter is paused waiting for page advance. */
    public boolean isPaused() {
        return paused;
    }

    /** Advance to the next page of text. */
    public void advancePage() {
        pageOffset += maxVisibleLines;
        paused = false;
        blinkTimer = 0;
    }

    /** Whether there are more pages after the current one (when typewriter is complete). */
    public boolean hasMorePages() {
        return complete && pageOffset + maxVisibleLines < fullLines.size();
    }

    public boolean isComplete() {
        return complete && !hasMorePages();
    }

    public boolean shouldShowIndicator() {
        return (paused || (complete && !hasMorePages())) && (blinkTimer / 10) % 2 == 0;
    }

    /** Show a page advance indicator (different from the final "done" indicator). */
    public boolean shouldShowPageIndicator() {
        return paused || hasMorePages();
    }

    public void skipToEnd() {
        if (paused) {
            // Paused at page boundary — advance to next page, resume typing
            advancePage();
        } else if (!complete) {
            // Still typing — reveal all remaining text on the current page
            // Find how many chars fill through the current page
            int charsUpToPageEnd = countCharsUpToLine(pageOffset + maxVisibleLines);
            revealedChars = Math.min(charsUpToPageEnd, totalChars);
            if (revealedChars >= totalChars) {
                complete = true;
            } else {
                // More pages — pause so the user can advance
                paused = true;
            }
        } else if (hasMorePages()) {
            // Typewriter done but more pages — advance
            advancePage();
        }
        // else: complete, no more pages — do nothing (ENDING state handles dismiss)
    }

    public boolean hasText() {
        return totalChars > 0;
    }

    public int getTotalChars() {
        return totalChars;
    }

    public void setSpeed(float baseSpeed) {
        this.baseSpeed = baseSpeed;
    }

    public DialogueEffects getEffectAt(int charIndex) {
        if (effectParseResult == null) return null;
        return effectParseResult.getEffectAt(charIndex);
    }

    public boolean hasEffectTags() {
        return effectParseResult != null && !effectParseResult.spans().isEmpty();
    }

    /** Count total characters in lines 0..lineIndex-1. */
    private int countCharsUpToLine(int lineIndex) {
        int chars = 0;
        int limit = Math.min(lineIndex, fullLines.size());
        for (int i = 0; i < limit; i++) {
            chars += charLength(fullLines.get(i));
        }
        return chars;
    }

    private int countRevealedLines() {
        int remaining = revealedChars;
        int lines = 0;
        for (FormattedCharSequence line : fullLines) {
            if (remaining <= 0) break;
            lines++;
            remaining -= charLength(line);
        }
        return lines;
    }

    private static int countChars(List<FormattedCharSequence> lines) {
        int count = 0;
        for (FormattedCharSequence line : lines) {
            count += charLength(line);
        }
        return count;
    }

    private static int charLength(FormattedCharSequence seq) {
        int[] count = {0};
        seq.accept((index, style, codePoint) -> {
            count[0]++;
            return true;
        });
        return count[0];
    }

    private static FormattedCharSequence truncate(FormattedCharSequence seq, int maxChars) {
        return (sink) -> {
            int[] remaining = {maxChars};
            return seq.accept((index, style, codePoint) -> {
                if (remaining[0] <= 0) return false;
                remaining[0]--;
                return sink.accept(index, style, codePoint);
            });
        };
    }
}
