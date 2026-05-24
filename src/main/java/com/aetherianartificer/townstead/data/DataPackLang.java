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
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Shared support for data-pack-distributed translatable strings.
 *
 * <p>Data packs reach the server but {@code assets/} resource-pack content does
 * not, so any {@code { "translate": … }} string a data pack defines would render
 * as a raw key on clients without a paired resource pack. This util scans
 * {@code data/<ns>/lang/en_us.json} sidecars (Townstead-private convention; same
 * JSON shape as a standard MC lang file) and resolves translate keys into the
 * Component's fallback slot, which the relevant sync packets carry to clients.
 * Resource packs can still override the key for other locales.</p>
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
    private static final String LANG_SIDECAR_PATH = "lang/en_us.json";

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

    /** Merge every {@code data/<ns>/lang/en_us.json} into one (key → English) map. */
    public static Map<String, String> loadLangIndex(ResourceManager rm) {
        return loadLangIndex(rm, UnaryOperator.identity());
    }

    /**
     * As {@link #loadLangIndex(ResourceManager)}, applying {@code valueTransform}
     * to each value (e.g. the calendar's named-placeholder rewrite). Silently
     * skips namespaces without a sidecar; warns on malformed files.
     */
    public static Map<String, String> loadLangIndex(ResourceManager rm, UnaryOperator<String> valueTransform) {
        Map<String, String> out = new HashMap<>();
        Map<ResourceLocation, Resource> sidecars =
                rm.listResources("lang", loc -> loc.getPath().equals(LANG_SIDECAR_PATH));
        for (Map.Entry<ResourceLocation, Resource> sidecar : sidecars.entrySet()) {
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
        return out;
    }

    /**
     * Parse a text value accepting a literal string, {@code { "text": … }}, or
     * {@code { "translate": … }}. A translate key resolved in {@code langIndex}
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
                return resolved != null
                        ? Component.translatableWithFallback(key, resolved)
                        : Component.translatable(key);
            }
            if (obj.has("text")) return Component.literal(obj.get("text").getAsString());
        }
        return Component.literal(context);
    }
}
