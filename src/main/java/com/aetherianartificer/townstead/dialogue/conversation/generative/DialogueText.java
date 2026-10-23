package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.aetherianartificer.townstead.data.DataPackLang;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fills a line's placeholders for one locale, including word forms chosen by gender or number. */
public final class DialogueText {
    private DialogueText() {}

    /** Meta fact naming the form a value takes by default, such as a person's gender for their own demonym. */
    public static final String AGREE = "agree";

    /** One placeholder value: its English text, a lang key for other locales, and facts such as gender or number. */
    public record Value(String english, @Nullable String key, Map<String, String> meta) {
        public String text(String locale) {
            return text(locale, null);
        }

        /**
         * The value in a locale, in {@code form} when the locale defines it as {@code <key>.<form>}. A value
         * that agrees with something tries {@code <key>.<agree>.<form>} first and {@code <key>.<agree>} last.
         */
        public String text(String locale, @Nullable String form) {
            if (key == null) return english;
            String agree = meta.get(AGREE);
            List<String> keys = new ArrayList<>(4);
            if (form != null && agree != null) keys.add(key + "." + agree + "." + form);
            if (form != null) keys.add(key + "." + form);
            if (agree != null) keys.add(key + "." + agree);
            keys.add(key);
            for (String candidate : keys) {
                String found = DataPackLang.find(candidate, locale);
                if (found != null) return found;
            }
            return english;
        }
    }

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{([a-z0-9_]+)(?:/([a-z0-9_.]+))?(?:\\.([a-z0-9_]+):([^}]*))?}");

    /**
     * Fills {@code {name}}, prints a form with {@code {name/form}}, and selects
     * {@code {name.fact:a=text|b=text|*=text}} by the value's facts.
     */
    public static String fill(String template, Map<String, Value> values, String locale) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Value value = values.get(m.group(1));
            String replacement;
            if (m.group(3) == null) {
                replacement = value == null ? "" : value.text(locale, m.group(2));
            } else {
                String have = value == null ? null : value.meta().get(m.group(3));
                String fallback = "";
                replacement = null;
                for (String option : m.group(4).split("\\|")) {
                    int eq = option.indexOf('=');
                    if (eq < 0) continue;
                    String when = option.substring(0, eq).trim(), text = option.substring(eq + 1);
                    if (when.equals("*")) fallback = text;
                    else if (when.equals(have)) { replacement = text; break; }
                }
                if (replacement == null) replacement = fallback;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Parts are whole sentences, so a value that opens one takes a capital. */
    public static String capitalize(String text) {
        if (text.isEmpty() || !Character.isLowerCase(text.charAt(0))) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
