package com.aetherianartificer.townstead.root.appearance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.aetherianartificer.townstead.root.GeneRange;

import java.util.ArrayList;
import java.util.List;

/**
 * An inheritable hair declaration backed by MCA's hair renderer. {@code enabled == null} means this node does not
 * decide and resolution continues toward its parent. The object form deliberately leaves
 * room for authored genetic colour constraints without changing the top-level data shape.
 */
public record HairPolicy(Boolean enabled, List<HairColorRange> colorRanges,
        List<HairColorChoice> colors, List<HairGradient> gradients) {

    public static final HairPolicy INHERIT = new HairPolicy(null, List.of(), List.of(), List.of());

    public HairPolicy {
        colorRanges = colorRanges == null ? List.of() : List.copyOf(colorRanges);
        colors = colors == null ? List.of() : List.copyOf(colors);
        gradients = gradients == null ? List.of() : List.copyOf(gradients);
    }

    public HairPolicy(Boolean enabled) {
        this(enabled, List.of(), List.of(), List.of());
    }

    public HairPolicy(Boolean enabled, List<HairColorRange> colorRanges) {
        this(enabled, colorRanges, List.of(), List.of());
    }

    public HairPolicy(Boolean enabled, List<HairColorRange> colorRanges,
            List<HairColorChoice> colors) {
        this(enabled, colorRanges, colors, List.of());
    }

    public boolean isSpecified() {
        return enabled != null || !colorRanges.isEmpty() || !colors.isEmpty() || !gradients.isEmpty();
    }

    /** Accepts either {@code "hair": false} or {@code "hair": {"enabled": false}}. */
    public static HairPolicy parse(JsonObject owner) {
        return parse(owner, java.util.Map.of());
    }

    /** As {@link #parse(JsonObject)}, resolving colour {@code name} translate keys against the pack's lang sidecar. */
    public static HairPolicy parse(JsonObject owner, java.util.Map<String, String> lang) {
        if (owner == null || !owner.has("hair")) return INHERIT;
        JsonElement value = owner.get("hair");
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return new HairPolicy(value.getAsBoolean(), List.of(), List.of(), List.of());
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            JsonElement enabled = object.get("enabled");
            Boolean enabledValue = enabled != null && enabled.isJsonPrimitive()
                    && enabled.getAsJsonPrimitive().isBoolean()
                    ? enabled.getAsBoolean() : null;
            List<HairColorRange> ranges = parseRanges(object);
            List<HairColorChoice> colors = parseColors(object, lang);
            List<HairGradient> gradients = parseGradients(object, lang);
            return enabledValue == null && ranges.isEmpty() && colors.isEmpty() && gradients.isEmpty()
                    ? INHERIT : new HairPolicy(enabledValue, ranges, colors, gradients);
        }
        return INHERIT;
    }

    /**
     * A colour {@code name}: a literal string, {@code {"text": …}}, or {@code {"translate": …}}
     * resolved through the pack's lang sidecar like every other data-pack display string.
     * Returns {resolved text, translate key}; the key is empty for a literal.
     */
    private static String[] parseName(JsonElement el, java.util.Map<String, String> lang) {
        if (el == null || el.isJsonNull()) return new String[]{"", ""};
        net.minecraft.network.chat.Component component =
                com.aetherianartificer.townstead.data.DataPackLang.parseComponent(el, "", lang);
        String key = component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                ? tc.getKey() : "";
        return new String[]{component.getString(), key};
    }

    private static List<HairGradient> parseGradients(JsonObject object, java.util.Map<String, String> lang) {
        JsonElement raw = object.has("gradients") ? object.get("gradients") : object.get("color_gradients");
        if (raw == null || !raw.isJsonArray()) return List.of();
        List<HairGradient> gradients = new ArrayList<>();
        for (JsonElement entry : raw.getAsJsonArray()) {
            if (!entry.isJsonObject()) continue;
            JsonObject gradient = entry.getAsJsonObject();
            List<Integer> stops = new ArrayList<>();
            JsonElement authoredStops = gradient.get("stops");
            if (authoredStops != null && authoredStops.isJsonArray()) {
                for (JsonElement stop : authoredStops.getAsJsonArray()) {
                    Integer rgb = stop.isJsonPrimitive() ? parseRgb(stop.getAsString()) : null;
                    if (rgb != null) stops.add(rgb);
                }
            } else {
                Integer from = parseRgb(stringValue(gradient, "from", null));
                Integer to = parseRgb(stringValue(gradient, "to", null));
                if (from != null) stops.add(from);
                if (to != null) stops.add(to);
            }
            if (stops.size() < 2) continue;
            int weight = positiveInt(gradient.get("weight"), 1);
            String space = stringValue(gradient, "space", "hsv");
            String[] name = parseName(gradient.get("name"), lang);
            gradients.add(new HairGradient(stops, weight, HairGradient.Space.parse(space), name[0], name[1]));
        }
        return List.copyOf(gradients);
    }

    private static List<HairColorChoice> parseColors(JsonObject object, java.util.Map<String, String> lang) {
        JsonElement raw = object.get("colors");
        if (raw == null || !raw.isJsonArray()) return List.of();
        List<HairColorChoice> colors = new ArrayList<>();
        for (JsonElement entry : raw.getAsJsonArray()) {
            String value = null;
            int weight = 1;
            String[] name = {"", ""};
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                value = entry.getAsString();
            } else if (entry.isJsonObject()) {
                JsonObject choice = entry.getAsJsonObject();
                if (choice.has("color") && choice.get("color").isJsonPrimitive()) {
                    value = choice.get("color").getAsString();
                }
                weight = positiveInt(choice.get("weight"), 1);
                name = parseName(choice.get("name"), lang);
            }
            Integer rgb = parseRgb(value);
            if (rgb != null) colors.add(new HairColorChoice(rgb, weight, name[0], name[1]));
        }
        return List.copyOf(colors);
    }

    private static Integer parseRgb(String raw) {
        if (raw == null) return null;
        String hex = raw.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() != 6) return null;
        try { return Integer.parseInt(hex, 16); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : fallback;
    }

    private static List<HairColorRange> parseRanges(JsonObject object) {
        JsonElement raw = object.get("color_ranges");
        if (raw == null || !raw.isJsonArray()) return List.of();
        List<HairColorRange> ranges = new ArrayList<>();
        for (JsonElement entry : raw.getAsJsonArray()) {
            if (!entry.isJsonObject()) continue;
            JsonObject range = entry.getAsJsonObject();
            GeneRange darkness = axis(range, "darkness", "eumelanin");
            GeneRange redness = axis(range, "redness", "pheomelanin");
            int weight = positiveInt(range.get("weight"), 1);
            ranges.add(new HairColorRange(darkness, redness, weight));
        }
        return List.copyOf(ranges);
    }

    private static GeneRange axis(JsonObject object, String friendly, String genetic) {
        JsonElement value = object.has(friendly) ? object.get(friendly) : object.get(genetic);
        if (value == null) return new GeneRange(0f, 1f);
        float min;
        float max;
        try {
            if (value.isJsonArray() && value.getAsJsonArray().size() >= 2) {
                min = value.getAsJsonArray().get(0).getAsFloat();
                max = value.getAsJsonArray().get(1).getAsFloat();
            } else if (value.isJsonObject()) {
                JsonObject range = value.getAsJsonObject();
                min = range.has("min") ? range.get("min").getAsFloat() : 0f;
                max = range.has("max") ? range.get("max").getAsFloat() : 1f;
            } else {
                min = max = value.getAsFloat();
            }
        } catch (RuntimeException ignored) {
            return new GeneRange(0f, 1f);
        }
        min = Math.max(0f, Math.min(1f, min));
        max = Math.max(0f, Math.min(1f, max));
        return new GeneRange(Math.min(min, max), Math.max(min, max));
    }

    private static int positiveInt(JsonElement value, int fallback) {
        try { return value == null ? fallback : Math.max(0, value.getAsInt()); }
        catch (RuntimeException ignored) { return fallback; }
    }
}
