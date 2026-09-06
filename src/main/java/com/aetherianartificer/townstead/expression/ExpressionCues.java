package com.aetherianartificer.townstead.expression;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.Map;

/** Transactional registry for {@code data/<namespace>/expression_cue/*.json}. */
public final class ExpressionCues {
    private static volatile Map<ResourceLocation, ExpressionCue> cues = Map.of();

    private ExpressionCues() {}

    public static ExpressionCue get(ResourceLocation id) { return cues.get(id); }
    public static Map<ResourceLocation, ExpressionCue> all() { return cues; }
    static void replaceAll(Map<ResourceLocation, ExpressionCue> values) { cues = Map.copyOf(values); }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(new Gson(), "expression_cue"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, ExpressionCue> parsed = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    parsed.put(entry.getKey(), ExpressionCue.parse(entry.getKey(),
                            GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString())));
                } catch (RuntimeException ex) {
                    Townstead.LOGGER.warn("Invalid expression cue {}: {}", entry.getKey(), ex.getMessage());
                }
            }
            replaceAll(parsed);
            Townstead.LOGGER.info("Loaded {} expression cues", parsed.size());
        }
    }
}
