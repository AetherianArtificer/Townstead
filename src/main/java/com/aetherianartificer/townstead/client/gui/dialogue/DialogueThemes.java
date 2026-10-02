package com.aetherianartificer.townstead.client.gui.dialogue;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dialogue themes from resource packs, {@code assets/<ns>/dialogue_theme/<id>.json}.
 *
 * <p>Themes stack field by field: the built-in classic look, then the theme the player picked,
 * then the speaker's own theme (a Persona's {@code dialogue_theme}) unless the player turned
 * character themes off. A theme sets only what it wants; the rest comes from the layer below.</p>
 */
public final class DialogueThemes {
    public static final ResourceLocation CLASSIC = ResourceLocation.tryParse("townstead:classic");
    private static final String DIR = "dialogue_theme";

    private static volatile Map<ResourceLocation, JsonObject> themes = Map.of();
    /** From the server's data packs and Personas; a resource-pack theme with the same id wins. */
    private static volatile Map<ResourceLocation, JsonObject> serverThemes = Map.of();
    private static final Map<String, DialogueTheme> RESOLVED = new HashMap<>();
    private static final DialogueTheme FALLBACK = DialogueTheme.parse(new JsonObject());

    private DialogueThemes() {}

    public static void reload(ResourceManager manager) {
        Map<ResourceLocation, JsonObject> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry
                : manager.listResources(DIR, path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = entry.getKey();
            String path = file.getPath().substring(DIR.length() + 1, file.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":" + path);
            if (id == null) continue;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                if (json.isJsonObject()) loaded.put(id, json.getAsJsonObject());
            } catch (Exception e) {
                Townstead.LOGGER.warn("[Townstead] Skipping dialogue theme {}: {}", id, e.getMessage());
            }
        }
        themes = Map.copyOf(loaded);
        synchronized (RESOLVED) {
            RESOLVED.clear();
        }
    }

    public static void setServer(Map<ResourceLocation, String> raw) {
        Map<ResourceLocation, JsonObject> loaded = new LinkedHashMap<>();
        raw.forEach((id, json) -> {
            try {
                JsonElement parsed = JsonParser.parseString(json);
                if (parsed.isJsonObject()) loaded.put(id, parsed.getAsJsonObject());
            } catch (Exception e) {
                Townstead.LOGGER.warn("[Townstead] Skipping dialogue theme {}: {}", id, e.getMessage());
            }
        });
        serverThemes = Map.copyOf(loaded);
        synchronized (RESOLVED) {
            RESOLVED.clear();
        }
    }

    /** The theme ids packs provide. */
    public static java.util.Set<ResourceLocation> ids() {
        java.util.Set<ResourceLocation> ids = new java.util.LinkedHashSet<>(themes.keySet());
        ids.addAll(serverThemes.keySet());
        return ids;
    }

    /** The look for a speaker whose own theme is {@code speakerTheme} (empty or null for none). */
    public static DialogueTheme resolve(@Nullable String speakerTheme) {
        String chosen = TownsteadConfig.dialogueTheme();
        String speaker = speakerTheme == null || !TownsteadConfig.characterDialogueThemes() ? "" : speakerTheme;
        String key = chosen + "|" + speaker;
        synchronized (RESOLVED) {
            DialogueTheme cached = RESOLVED.get(key);
            if (cached != null) return cached;
            JsonObject merged = new JsonObject();
            layer(merged, CLASSIC.toString());
            layer(merged, chosen);
            layer(merged, speaker);
            DialogueTheme theme;
            try {
                theme = DialogueTheme.parse(merged);
            } catch (Exception e) {
                theme = FALLBACK;
            }
            RESOLVED.put(key, theme);
            return theme;
        }
    }

    private static void layer(JsonObject into, String id) {
        if (id == null || id.isEmpty()) return;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        JsonObject theme = rl == null ? null : themes.getOrDefault(rl, serverThemes.get(rl));
        if (theme != null) merge(into, theme);
    }

    private static void merge(JsonObject into, JsonObject from) {
        for (Map.Entry<String, JsonElement> e : from.entrySet()) {
            if (e.getValue().isJsonObject() && into.has(e.getKey()) && into.get(e.getKey()).isJsonObject()) {
                merge(into.getAsJsonObject(e.getKey()), e.getValue().getAsJsonObject());
            } else {
                into.add(e.getKey(), e.getValue().deepCopy());
            }
        }
    }
}
