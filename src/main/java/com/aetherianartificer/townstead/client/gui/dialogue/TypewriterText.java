package com.aetherianartificer.townstead.client.gui.dialogue;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages character-by-character reveal of a {@link Component}, preserving formatting.
 */
public class TypewriterText {
    private Component fullText = Component.empty();
    private List<FormattedCharSequence> fullLines = List.of();
    private int totalChars;
    private int revealedChars;
    private int tickCounter;
    private int charsPerTick = 1;
    private int ticksPerChar = 1;
    private boolean complete;
    private int blinkTimer;

    public void setText(Component text, Font font, int maxWidth) {
        this.fullText = text;
        this.fullLines = font.split(text, maxWidth);
        this.totalChars = countChars(fullLines);
        this.revealedChars = 0;
        this.tickCounter = 0;
        this.complete = false;
        this.blinkTimer = 0;
    }

    public void tick() {
        if (complete) {
            blinkTimer++;
            return;
        }
        tickCounter++;
        if (tickCounter >= ticksPerChar) {
            tickCounter = 0;
            revealedChars = Math.min(revealedChars + charsPerTick, totalChars);
            if (revealedChars >= totalChars) {
                complete = true;
            }
        }
    }

    public List<FormattedCharSequence> getRevealedLines() {
        if (complete) {
            return fullLines;
        }
        List<FormattedCharSequence> result = new ArrayList<>();
        int remaining = revealedChars;
        for (FormattedCharSequence line : fullLines) {
            int lineLen = charLength(line);
            if (remaining <= 0) {
                break;
            }
            if (remaining >= lineLen) {
                result.add(line);
                remaining -= lineLen;
            } else {
                result.add(truncate(line, remaining));
                break;
            }
        }
        return result;
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean shouldShowIndicator() {
        return complete && (blinkTimer / 10) % 2 == 0;
    }

    public void skipToEnd() {
        revealedChars = totalChars;
        complete = true;
    }

    public boolean hasText() {
        return totalChars > 0;
    }

    public void setSpeed(int charsPerTick, int ticksPerChar) {
        this.charsPerTick = Math.max(1, charsPerTick);
        this.ticksPerChar = Math.max(1, ticksPerChar);
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
                if (remaining[0] <= 0) {
                    return false;
                }
                remaining[0]--;
                return sink.accept(index, style, codePoint);
            });
        };
    }
}
