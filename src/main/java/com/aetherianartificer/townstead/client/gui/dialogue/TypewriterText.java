package com.aetherianartificer.townstead.client.gui.dialogue;

import com.aetherianartificer.townstead.client.gui.dialogue.effect.DialogueEffects;
import com.aetherianartificer.townstead.client.gui.dialogue.effect.EffectTagParser;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages character-by-character reveal of a {@link Component}, preserving formatting.
 * Supports inline effect tags and per-effect typewriter speed.
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

    // Parsed effect spans from inline tags
    private EffectTagParser.ParseResult effectParseResult;

    public void setText(Component text, Font font, int maxWidth) {
        // Check if the text contains effect tags
        String rawText = text.getString();
        if (rawText.contains("<") && rawText.contains(">")) {
            effectParseResult = EffectTagParser.parse(rawText);
            // Re-create the component with clean text (tags stripped)
            this.fullText = Component.literal(effectParseResult.cleanText());
        } else {
            effectParseResult = null;
            this.fullText = text;
        }

        this.fullLines = font.split(this.fullText, maxWidth);
        this.totalChars = countChars(fullLines);
        this.revealedChars = 0;
        this.revealAccumulator = 0;
        this.complete = false;
        this.blinkTimer = 0;
    }

    public void tick() {
        if (complete) {
            blinkTimer++;
            return;
        }

        // Determine speed for current character
        float speed = baseSpeed;
        DialogueEffects charEffect = getEffectAt(revealedChars);
        if (charEffect != null) {
            speed *= charEffect.getTypewriterSpeed();
        }

        revealAccumulator += speed;
        while (revealAccumulator >= 1.0f && !complete) {
            revealAccumulator -= 1.0f;
            revealedChars++;
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

    public int getTotalChars() {
        return totalChars;
    }

    public void setSpeed(float baseSpeed) {
        this.baseSpeed = baseSpeed;
    }

    /** Get the inline effect at a given character index, or null. */
    public DialogueEffects getEffectAt(int charIndex) {
        if (effectParseResult == null) return null;
        return effectParseResult.getEffectAt(charIndex);
    }

    /** Whether this text has any inline effect tags. */
    public boolean hasEffectTags() {
        return effectParseResult != null && !effectParseResult.spans().isEmpty();
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
