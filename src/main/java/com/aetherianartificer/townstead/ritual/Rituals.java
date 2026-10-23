package com.aetherianartificer.townstead.ritual;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.ModGate;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Loaded ritual definitions, from {@code data/<ns>/ritual/}. */
public final class Rituals {
    private static volatile Map<ResourceLocation, RitualDefinition> loaded = Map.of();

    private Rituals() {}

    public static @Nullable RitualDefinition get(ResourceLocation id) {
        return loaded.get(id);
    }

    public static Set<ResourceLocation> ids() {
        return loaded.keySet();
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(new Gson(), "ritual"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, RitualDefinition> parsed = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                    if (!ModGate.allows(json)) continue;
                    parsed.put(entry.getKey(), RitualDefinition.parse(entry.getKey(), json));
                } catch (Exception exception) {
                    Townstead.LOGGER.warn("Ritual {} rejected: {}", entry.getKey(), exception.getMessage());
                }
            }
            loaded = Map.copyOf(parsed);
            Townstead.LOGGER.info("Loaded {} rituals", parsed.size());
        }
    }
}
