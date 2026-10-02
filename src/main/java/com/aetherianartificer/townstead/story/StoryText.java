package com.aetherianartificer.townstead.story;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Names in story lines that the reader's client translates. The server writes a token such as
 * {@code [[building:bakery]]} or {@code [[item:minecraft:wheat]]}; the client replaces it with
 * the name in the reader's language, from {@code buildingType.<id>} or the item's own name.
 */
public final class StoryText {
    private StoryText() {}

    private static final Pattern TOKEN = Pattern.compile("\\[\\[(building|item):([a-z0-9_.:/-]+)]]");

    public static String building(String type) {
        return "[[building:" + type + "]]";
    }

    public static String item(ResourceLocation id) {
        return "[[item:" + id + "]]";
    }

    /** Replaces every token using {@code translate}, which gets a translation key. */
    public static String resolve(String text, UnaryOperator<String> translate) {
        if (text == null || !text.contains("[[")) return text;
        Matcher m = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1).equals("building") ? "buildingType." + m.group(2) : itemKey(m.group(2));
            m.appendReplacement(out, Matcher.quoteReplacement(translate.apply(key)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** The line as chat text: tokens become names the reader's client translates. */
    public static net.minecraft.network.chat.MutableComponent component(String text) {
        net.minecraft.network.chat.MutableComponent out = net.minecraft.network.chat.Component.empty();
        Matcher m = TOKEN.matcher(text);
        int from = 0;
        while (m.find()) {
            if (m.start() > from) out.append(text.substring(from, m.start()));
            String key = m.group(1).equals("building") ? "buildingType." + m.group(2) : itemKey(m.group(2));
            out.append(net.minecraft.network.chat.Component.translatable(key));
            from = m.end();
        }
        if (from < text.length()) out.append(text.substring(from));
        return out;
    }

    private static String itemKey(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return raw;
        return BuiltInRegistries.ITEM.get(id).getDescriptionId();
    }
}
