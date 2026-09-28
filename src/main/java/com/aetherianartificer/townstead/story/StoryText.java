package com.aetherianartificer.townstead.story;

import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Names in story lines that the reader's client translates. The server writes a token such as
 * {@code [[building:bakery]]}; the client replaces it with the building's name in the reader's
 * language, from {@code buildingType.<id>}.
 */
public final class StoryText {
    private StoryText() {}

    private static final Pattern TOKEN = Pattern.compile("\\[\\[building:([a-z0-9_./-]+)]]");

    public static String building(String type) {
        return "[[building:" + type + "]]";
    }

    /** Replaces every token using {@code translate}, which gets a translation key. */
    public static String resolve(String text, UnaryOperator<String> translate) {
        if (text == null || !text.contains("[[")) return text;
        Matcher m = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(translate.apply("buildingType." + m.group(1))));
        m.appendTail(out);
        return out.toString();
    }
}
