package com.aetherianartificer.townstead.dialogue;

import com.aetherianartificer.townstead.persona.PersonaDefinition;
import com.aetherianartificer.townstead.persona.Personas;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dialogue themes from data packs, {@code data/<ns>/dialogue_theme/<id>.json}, plus the themes
 * Personas write inline in persona.json. Sent to every client, which layers them with the themes
 * from its resource packs (see {@code DialogueThemes}).
 */
public final class DialogueThemeData {
    private static volatile Map<ResourceLocation, String> themes = Map.of();

    private DialogueThemeData() {}

    /** Every data-pack theme and every Persona's inline theme, as JSON text by id. */
    public static Map<ResourceLocation, String> all() {
        Map<ResourceLocation, String> out = new LinkedHashMap<>(themes);
        for (PersonaDefinition persona : Personas.all().values()) {
            if (persona.dialogueThemeJson() != null && persona.dialogueTheme() != null) {
                out.put(persona.dialogueTheme(), persona.dialogueThemeJson().toString());
            }
        }
        return out;
    }

    /** The id an inline theme in persona.json goes by. */
    public static ResourceLocation inlineId(ResourceLocation persona) {
        return ResourceLocation.tryParse(persona.getNamespace() + ":persona/" + persona.getPath());
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() {
            super(new Gson(), "dialogue_theme");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, String> loaded = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
                if (file.getValue().isJsonObject()) loaded.put(file.getKey(), file.getValue().toString());
            }
            themes = Map.copyOf(loaded);
        }
    }
}
