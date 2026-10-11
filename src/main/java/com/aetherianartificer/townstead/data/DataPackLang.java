package com.aetherianartificer.townstead.data;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Shared support for data-pack-distributed translatable strings.
 *
 * <p>Data packs reach the server but {@code assets/} resource-pack content does
 * not, so any {@code { "translate": … }} string a data pack defines would render
 * as a raw key on clients without a paired resource pack. This util scans
 * {@code data/<ns>/lang/<locale>.json} sidecars (Townstead-private convention;
 * same JSON shape as a standard MC lang file). English values become the
 * default fallbacks; per-player sync can replace them with values for the
 * receiving player's locale. Resource packs can still override the keys.</p>
 *
 * <p>The scan uses {@link ResourceManager#listResources} rather than
 * {@code getNamespaces()} because on 1.20.1 Forge a data-only namespace can be
 * absent from {@code getNamespaces()}, which silently drops its sidecar.</p>
 *
 * <p>Extracted from the calendar profile loader so the origin loaders share one
 * sidecar index and one component parser.</p>
 */
public final class DataPackLang {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/DataPackLang");
    private static final Gson GSON = new Gson();
    private static final String LANG_PREFIX = "lang/";
    private static final String LANG_SUFFIX = ".json";
    private static final String DEFAULT_LOCALE = "en_us";
    private static volatile Map<String, Map<String, String>> LOCALES = Map.of();

    private DataPackLang() {}

    /**
     * Version-agnostic {@code ResourceLocation.tryParse}: returns the parsed id
     * or {@code null} for null/blank/invalid input. Confines the 1.20.1-vs-1.21
     * construction difference to one place so data loaders stay branch-agnostic.
     */
    public static net.minecraft.resources.ResourceLocation parseId(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            //? if >=1.21 {
            return net.minecraft.resources.ResourceLocation.parse(s);
            //?} else {
            /*return new net.minecraft.resources.ResourceLocation(s);
            *///?}
        } catch (Exception ex) {
            return null;
        }
    }

    /** Load all locale sidecars and return the merged English fallback map. */
    public static Map<String, String> loadLangIndex(ResourceManager rm) {
        return loadLangIndex(rm, UnaryOperator.identity());
    }

    /**
     * As {@link #loadLangIndex(ResourceManager)}, applying {@code valueTransform}
     * to each value (e.g. the calendar's named-placeholder rewrite). Loads all
     * locales, skips namespaces without sidecars, and warns on malformed files.
     */
    public static Map<String, String> loadLangIndex(ResourceManager rm, UnaryOperator<String> valueTransform) {
        Map<String, Map<String, String>> locales = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> sidecars =
                rm.listResources("lang", loc -> {
                    String path = loc.getPath();
                    return path.startsWith(LANG_PREFIX) && path.endsWith(LANG_SUFFIX);
                });
        for (Map.Entry<ResourceLocation, Resource> sidecar : sidecars.entrySet()) {
            String path = sidecar.getKey().getPath();
            String locale = normalizeLocale(path.substring(
                    LANG_PREFIX.length(), path.length() - LANG_SUFFIX.length()));
            Map<String, String> out = locales.computeIfAbsent(locale, ignored -> new HashMap<>());
            try (Reader r = sidecar.getValue().openAsReader()) {
                JsonElement el = GSON.fromJson(r, JsonElement.class);
                if (el == null || !el.isJsonObject()) continue;
                for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                    JsonElement v = e.getValue();
                    if (v != null && v.isJsonPrimitive()) {
                        out.put(e.getKey(), valueTransform.apply(v.getAsString()));
                    }
                }
            } catch (Exception ex) {
                LOGGER.warn("Failed to read lang sidecar {}: {}", sidecar.getKey(), ex.getMessage());
            }
        }
        Map<String, Map<String, String>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : locales.entrySet()) {
            immutable.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        LOCALES = Map.copyOf(immutable);
        return LOCALES.getOrDefault(DEFAULT_LOCALE, Map.of());
    }

    /**
     * Resolve a key for a player locale then English, or {@code null} if absent in both. Unlike
     * {@link #resolveFallback} this distinguishes "missing" from a fallback value, so callers can
     * probe for the existence of a key (e.g. counting numbered dialogue variants).
     */
    public static String find(String key, String locale) {
        if (key == null || key.isEmpty()) return null;
        String normalized = normalizeLocale(locale);
        String resolved = lookup(key, normalized);
        if (resolved == null) {
            String remapped = com.aetherianartificer.townstead.root.LegacyNamespace.remapLangKey(key);
            if (remapped != null) resolved = lookup(remapped, normalized);
        }
        return resolved;
    }

    /** Resolve a key for a player locale, then English, then the supplied fallback. */
    public static String resolveFallback(String key, String locale, String fallback) {
        String resolved = find(key, locale);
        return resolved != null ? resolved : (fallback != null ? fallback : "");
    }

    /** Locale-then-English lookup for one key as-given; the legacy remap lives in {@link #find}. */
    private static String lookup(String key, String normalizedLocale) {
        String resolved = LOCALES.getOrDefault(normalizedLocale, Map.of()).get(key);
        if (resolved == null && !DEFAULT_LOCALE.equals(normalizedLocale)) {
            resolved = LOCALES.getOrDefault(DEFAULT_LOCALE, Map.of()).get(key);
        }
        return resolved;
    }

    private static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) return DEFAULT_LOCALE;
        return locale.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    /**
     * Parse a text value accepting a literal string, {@code { "text": … }}, or
     * {@code { "translate": …, "with": [ … ] }}, whose arguments are parsed the same way. A translate key resolved in {@code langIndex}
     * gets that English string as its fallback. Returns a literal of
     * {@code context} for null/unrecognized input.
     */
    public static Component parseComponent(JsonElement el, String context, Map<String, String> langIndex) {
        if (el == null || el.isJsonNull()) return Component.literal(context);
        if (el.isJsonPrimitive()) return Component.literal(el.getAsString());
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("translate")) {
                String key = obj.get("translate").getAsString();
                String resolved = langIndex.get(key);
                if (resolved == null) {
                    // Old packs reference mod-owned keys under the pre-rebrand namespace segment;
                    // only the remapped key still resolves (lang files or a sidecar), so carry it.
                    String remapped = com.aetherianartificer.townstead.root.LegacyNamespace.remapLangKey(key);
                    if (remapped != null && langIndex.containsKey(remapped)) {
                        key = remapped;
                        resolved = langIndex.get(remapped);
                    }
                }
                if (obj.has("with") && obj.get("with").isJsonArray()) {
                    // Arguments are components in their own right (e.g. a qualifier and a name).
                    com.google.gson.JsonArray with = obj.getAsJsonArray("with");
                    Object[] args = new Object[with.size()];
                    String[] plain = new String[with.size()];
                    for (int i = 0; i < with.size(); i++) {
                        args[i] = parseComponent(with.get(i), context + ".with[" + i + "]", langIndex);
                        plain[i] = plainText(with.get(i), langIndex);
                    }
                    if (resolved == null) return Component.translatable(key, args);
                    String fallback;
                    try {
                        fallback = String.format(Locale.ROOT, resolved, (Object[]) plain);
                    } catch (java.util.IllegalFormatException ex) {
                        fallback = resolved;
                    }
                    return Component.translatableWithFallback(key, fallback, args);
                }
                return resolved != null
                        ? Component.translatableWithFallback(key, resolved)
                        : Component.translatable(key);
            }
            if (obj.has("text")) return Component.literal(obj.get("text").getAsString());
        }
        return Component.literal(context);
    }

    /** English text of a component JSON from the lang index alone, without the client language. */
    private static String plainText(JsonElement el, Map<String, String> langIndex) {
        if (el == null || el.isJsonNull()) return "";
        if (el.isJsonPrimitive()) return el.getAsString();
        if (!el.isJsonObject()) return "";
        JsonObject obj = el.getAsJsonObject();
        if (obj.has("text")) return obj.get("text").getAsString();
        if (!obj.has("translate")) return "";
        String key = obj.get("translate").getAsString();
        String template = langIndex.getOrDefault(key, key);
        if (!obj.has("with") || !obj.get("with").isJsonArray()) return template;
        com.google.gson.JsonArray with = obj.getAsJsonArray("with");
        Object[] args = new Object[with.size()];
        for (int i = 0; i < with.size(); i++) args[i] = plainText(with.get(i), langIndex);
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (java.util.IllegalFormatException ex) {
            return template;
        }
    }
}
