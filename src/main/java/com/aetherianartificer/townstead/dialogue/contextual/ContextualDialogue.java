package com.aetherianartificer.townstead.dialogue.contextual;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Transactional registry for {@code data/<namespace>/dialogue_palette/*.json}. */
public final class ContextualDialogue {
    private static volatile Map<ResourceLocation, DialoguePalette> palettes = Map.of();

    private ContextualDialogue() {}
    public static Map<ResourceLocation, DialoguePalette> all() { return palettes; }
    public static List<DialoguePalette> forIntent(String intent) {
        String key = DialoguePalette.normalized(intent);
        return palettes.values().stream().filter(value -> value.intent().equals(key)).toList();
    }
    static void replaceAll(Map<ResourceLocation, DialoguePalette> values) { palettes = Map.copyOf(values); }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(new Gson(), "dialogue_palette"); }
        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, DialoguePalette> parsed = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    parsed.put(entry.getKey(), DialoguePalette.parse(entry.getKey(),
                            GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString())));
                } catch (RuntimeException ex) {
                    Townstead.LOGGER.warn("Invalid dialogue palette {}: {}", entry.getKey(), ex.getMessage());
                }
            }
            replaceAll(parsed);
            DialogueDirector.clear();
            Townstead.LOGGER.info("Loaded {} contextual dialogue palettes", parsed.size());
        }
    }
}
