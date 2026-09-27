package com.aetherianartificer.townstead.livery;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code livery_style} documents.
 *
 * <pre>{@code
 * {
 *   "schema": "townstead:livery_style/v1",
 *   "name": { "translate": "livery_style.example_pack.lacquered_plate" },
 *   "tint": true,
 *   "colors": { "primary": "#8A1C1C", "secondary": "#C9A227" },
 *   "trims": { "chest": { "pattern": "minecraft:ward", "material": "minecraft:gold" } }
 * }
 * }</pre>
 */
public final class LiveryStyles {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/LiveryStyles");
    private static final List<String> SLOTS = List.of("head", "chest", "legs", "feet");
    private static volatile Map<ResourceLocation, LiveryStyle> styles = Map.of();

    private LiveryStyles() {}

    public static @Nullable LiveryStyle get(@Nullable ResourceLocation id) {
        return id == null ? null : styles.get(id);
    }

    public static Map<ResourceLocation, LiveryStyle> all() { return styles; }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(new Gson(), "livery_style"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, LiveryStyle> loaded = new LinkedHashMap<>();
            Map<String, String> lang = com.aetherianartificer.townstead.data.DataPackLang.loadLangIndex(manager);
            entries.forEach((id, element) -> {
                try {
                    loaded.put(id, parse(id, GsonHelper.convertToJsonObject(element, "livery style"), lang));
                } catch (RuntimeException error) {
                    LOGGER.warn("Skipping livery style {}: {}", id, error.getMessage());
                }
            });
            styles = Map.copyOf(loaded);
        }
    }

    /** {@code name} is a literal, or a translation key a pack's lang sidecar resolves. */
    static LiveryStyle parse(ResourceLocation id, JsonObject json, Map<String, String> lang) {
        Component name = com.aetherianartificer.townstead.data.DataPackLang.parseComponent(json.get("name"), id.getPath(), lang);
        JsonObject colours = GsonHelper.getAsJsonObject(json, "colors", new JsonObject());
        int primary = colour(GsonHelper.getAsString(colours, "primary", "#FFFFFF"));
        int secondary = colour(GsonHelper.getAsString(colours, "secondary", "#FFFFFF"));
        Map<String, LiveryView.Trim> trims = new LinkedHashMap<>();
        JsonObject trimJson = GsonHelper.getAsJsonObject(json, "trims", new JsonObject());
        for (String slot : SLOTS) {
            if (!trimJson.has(slot)) continue;
            JsonObject trim = GsonHelper.getAsJsonObject(trimJson, slot);
            trims.put(slot, new LiveryView.Trim(GsonHelper.getAsString(trim, "pattern"), GsonHelper.getAsString(trim, "material")));
        }
        return new LiveryStyle(id, name, GsonHelper.getAsBoolean(json, "tint", false), primary, secondary, trims);
    }

    static int colour(String raw) {
        String value = raw.startsWith("#") ? raw.substring(1) : raw;
        if (value.length() != 6) throw new IllegalArgumentException("colour must be #RRGGBB: " + raw);
        return Integer.parseInt(value, 16);
    }
}
