package com.aetherianartificer.townstead.client.gui.dialogue.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses inline effect tags like {@code <happy>text</happy>} from dialogue strings.
 * Returns the clean text (tags stripped) and a list of effect spans.
 */
public final class EffectTagParser {
    private EffectTagParser() {}

    /** A span of text that has a specific effect applied. */
    public record EffectSpan(int startIndex, int endIndex, DialogueEffects effect) {
        public boolean contains(int charIndex) {
            return charIndex >= startIndex && charIndex < endIndex;
        }
    }

    /** Result of parsing: clean text + effect spans. */
    public record ParseResult(String cleanText, List<EffectSpan> spans) {
        /** Get the effect for a given character index, or null if no tag covers it. */
        public DialogueEffects getEffectAt(int charIndex) {
            for (EffectSpan span : spans) {
                if (span.contains(charIndex)) {
                    return span.effect;
                }
            }
            return null;
        }
    }

    private static final Pattern TAG_PATTERN = Pattern.compile("<(\\w+)>(.*?)</\\1>", Pattern.DOTALL);
    private static final Pattern STRIP_PATTERN = Pattern.compile("<\\w+>(.*?)</\\w+>", Pattern.DOTALL);

    /**
     * Strip all effect tags from a string, returning just the plain text.
     * Used for chat messages where tags should not be visible.
     */
    public static String stripTags(String input) {
        String result = input;
        // Repeatedly strip until no more tags (handles nesting)
        while (STRIP_PATTERN.matcher(result).find()) {
            result = STRIP_PATTERN.matcher(result).replaceAll("$1");
        }
        return result;
    }

    /**
     * Parse effect tags from the input string. Tags can be nested conceptually
     * but the regex handles them left-to-right (no actual nesting).
     *
     * @param input raw text possibly containing tags like {@code <angry>Watch out!</angry>}
     * @return parsed result with clean text and effect spans
     */
    public static ParseResult parse(String input) {
        List<EffectSpan> spans = new ArrayList<>();
        StringBuilder clean = new StringBuilder();
        int lastEnd = 0;

        Matcher matcher = TAG_PATTERN.matcher(input);
        while (matcher.find()) {
            // Append text before this tag
            clean.append(input, lastEnd, matcher.start());
            int spanStart = clean.length();

            String tagName = matcher.group(1).toUpperCase(Locale.ROOT);
            String innerText = matcher.group(2);

            // Recursively parse inner text for nested tags
            ParseResult inner = parse(innerText);
            clean.append(inner.cleanText);
            int spanEnd = clean.length();

            // Resolve the effect
            DialogueEffects effect = resolveEffect(tagName);
            if (effect != null && effect != DialogueEffects.NORMAL) {
                spans.add(new EffectSpan(spanStart, spanEnd, effect));
            }

            // Add inner spans offset by spanStart
            for (EffectSpan innerSpan : inner.spans) {
                spans.add(new EffectSpan(
                        spanStart + innerSpan.startIndex,
                        spanStart + innerSpan.endIndex,
                        innerSpan.effect
                ));
            }

            lastEnd = matcher.end();
        }

        // Append remaining text
        clean.append(input, lastEnd, input.length());

        return new ParseResult(clean.toString(), spans);
    }

    private static DialogueEffects resolveEffect(String name) {
        try {
            return DialogueEffects.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
