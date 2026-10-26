package com.aetherianartificer.townstead.root.gene;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The codec for an allele's string payload ({@code Allele.variantId}), which has
 * grown three shapes over the feature's life — all still decodable:
 *
 * <ul>
 *   <li>{@code "bushy"} — a variant id (variant genes).</li>
 *   <li>{@code "1.030"} — a legacy single size roll (sized attachments before
 *       channels); reads as the anonymous channel {@code ""}.</li>
 *   <li>{@code "length=1.030;droop=0.400"} — named channel rolls.</li>
 *   <li>{@code "bushy|length=1.030"} — a variant id plus that variant's channel
 *       rolls (a variant-swapped attachment that also morphs).</li>
 * </ul>
 *
 * <p>Encoding is canonical (channels sorted by name, {@code %.3f}) and collapses
 * to the simplest legacy form — a bare variant id, or a bare float for a single
 * anonymous channel — so pre-channel content round-trips byte-identical.</p>
 */
public record AllelePayload(String variant, Map<String, Float> channels) {

    /** The anonymous channel a legacy single-float payload reads as. */
    public static final String LEGACY_CHANNEL = "";

    public static final AllelePayload EMPTY = new AllelePayload("", Map.of());

    public AllelePayload {
        variant = variant == null ? "" : variant;
        channels = channels == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(channels));
    }

    public static AllelePayload parse(String raw) {
        if (raw == null || raw.isEmpty()) return EMPTY;
        int bar = raw.indexOf('|');
        if (bar >= 0) {
            return new AllelePayload(raw.substring(0, bar), parseChannels(raw.substring(bar + 1)));
        }
        if (raw.indexOf('=') >= 0) return new AllelePayload("", parseChannels(raw));
        Float single = parseFloat(raw);
        if (single != null) return new AllelePayload("", Map.of(LEGACY_CHANNEL, single));
        return new AllelePayload(raw, Map.of());
    }

    public static String encode(String variant, Map<String, Float> channels) {
        String v = variant == null ? "" : variant;
        if (channels == null || channels.isEmpty()) return v;
        // Canonical: sorted names, %.3f. A lone anonymous channel keeps the legacy bare-float form.
        TreeMap<String, Float> sorted = new TreeMap<>(channels);
        StringBuilder out = new StringBuilder();
        if (sorted.size() == 1 && sorted.containsKey(LEGACY_CHANNEL)) {
            out.append(fmt(sorted.get(LEGACY_CHANNEL)));
        } else {
            boolean first = true;
            for (Map.Entry<String, Float> channel : sorted.entrySet()) {
                if (channel.getKey().isEmpty()) continue;   // anonymous can't mix with named
                if (!first) out.append(';');
                out.append(channel.getKey()).append('=').append(fmt(channel.getValue()));
                first = false;
            }
        }
        if (v.isEmpty()) return out.toString();
        return out.isEmpty() ? v : v + "|" + out;
    }

    public String encode() {
        return encode(variant, channels);
    }

    /**
     * The value of a named channel; a payload holding only the anonymous legacy
     * channel answers for any name (it predates the channel having one).
     */
    public float channel(String name, float fallback) {
        Float value = channels.get(name);
        if (value == null && channels.size() == 1) value = channels.get(LEGACY_CHANNEL);
        return value == null ? fallback : value;
    }

    public boolean hasChannels() {
        return !channels.isEmpty();
    }

    private static Map<String, Float> parseChannels(String raw) {
        Map<String, Float> out = new LinkedHashMap<>();
        for (String part : raw.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            Float value = parseFloat(part.substring(eq + 1));
            if (value != null) out.put(part.substring(0, eq), value);
        }
        return out;
    }

    private static Float parseFloat(String raw) {
        // Most payloads are variant names; skip the exception a failed parse costs.
        char first = raw.isEmpty() ? 'x' : raw.charAt(0);
        if (!(first >= '0' && first <= '9') && first != '-' && first != '+' && first != '.') return null;
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
