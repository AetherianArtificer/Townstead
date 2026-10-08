package com.aetherianartificer.townstead.dialogue.conversation;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.*;

public final class ConversationTopics {
    private static volatile Map<ResourceLocation, ConversationTopic> topics = Map.of();
    private static volatile List<String> diagnostics = List.of();
    private ConversationTopics() {}
    public static Map<ResourceLocation, ConversationTopic> all() { return topics; }
    public static List<String> diagnostics() { return diagnostics; }
    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(new Gson(), "conversation"); }
        @Override protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, ConversationTopic> next = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
            List<String> errors = new ArrayList<>();
            entries.forEach((id, json) -> {
                try { next.put(id, ConversationTopic.parse(id, json.getAsJsonObject())); }
                catch (RuntimeException ex) { errors.add(id + " $." + ex.getMessage()); }
            });
            ConversationEngine.clear();
            topics = Collections.unmodifiableMap(next); diagnostics = List.copyOf(errors);
            errors.forEach(error -> Townstead.LOGGER.warn("Conversation rejected: {}", error));
            Townstead.LOGGER.info("Loaded {} conversation topics ({} rejected)", topics.size(), errors.size());
        }
    }
}
