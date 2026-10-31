package com.aetherianartificer.townstead.food;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Selector-only datapack catalogue for blocks that are valid serving surfaces. */
public final class ServingSurfaces {
    public static final String SCHEMA = "townstead:serving_plate/v1";
    private static final Set<String> ROOT_FIELDS = Set.of("schema", "block", "blocks");
    private static final Set<String> ENTRY_FIELDS = Set.of("id", "required");
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/ServingSurfaces");
    private static volatile Set<ResourceLocation> BLOCKS = Set.of(
            ResourceLocation.tryParse("townstead:serving_plate"));

    private ServingSurfaces() {}

    public static boolean contains(BlockState state) {
        return state != null && BLOCKS.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public static Set<ResourceLocation> blockIds() { return BLOCKS; }

    /** Loads and merges data/&lt;namespace&gt;/serving_plate/*.json from every datapack. */
    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(new Gson(), "serving_plate"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resources,
                             ProfilerFiller profiler) {
            Set<ResourceLocation> blocks = new LinkedHashSet<>();
            entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                try {
                    JsonObject root = entry.getValue().getAsJsonObject();
                    TownsteadSchema.validateRequired(root, SCHEMA);
                    rejectUnknown(root, ROOT_FIELDS, "serving plate document");
                    boolean singular = root.has("block");
                    boolean plural = root.has("blocks");
                    if (singular == plural) {
                        throw new IllegalArgumentException("Exactly one of 'block' or 'blocks' is required");
                    }
                    if (singular) add(blocks, root.get("block"));
                    if (plural) {
                        if (!root.get("blocks").isJsonArray() || root.getAsJsonArray("blocks").isEmpty()) {
                            throw new IllegalArgumentException("'blocks' must be a non-empty array");
                        }
                        for (JsonElement value : root.getAsJsonArray("blocks")) add(blocks, value);
                    }
                } catch (Exception ex) {
                    LOGGER.warn("Failed to parse serving_plate {}: {}", entry.getKey(), ex.getMessage());
                }
            });
            BLOCKS = Set.copyOf(blocks);
            LOGGER.info("Loaded {} serving plate block selectors", blocks.size());
        }

        private static void add(Set<ResourceLocation> out, JsonElement value) {
            String raw;
            boolean required = true;
            if (value.isJsonPrimitive()) {
                raw = value.getAsString();
            } else {
                JsonObject object = value.getAsJsonObject();
                rejectUnknown(object, ENTRY_FIELDS, "block selector");
                if (!object.has("id") || !object.get("id").isJsonPrimitive()
                        || !object.getAsJsonPrimitive("id").isString()) {
                    throw new IllegalArgumentException("Block selector 'id' must be a string");
                }
                raw = object.get("id").getAsString();
                if (object.has("required") && (!object.get("required").isJsonPrimitive()
                        || !object.getAsJsonPrimitive("required").isBoolean())) {
                    throw new IllegalArgumentException("Block selector 'required' must be a boolean");
                }
                required = !object.has("required") || object.get("required").getAsBoolean();
            }
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) throw new IllegalArgumentException("Invalid block id " + raw);
            if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                if (required) LOGGER.warn("Serving plate block {} is not installed", id);
                return;
            }
            out.add(id);
        }

        private static void rejectUnknown(JsonObject object, Set<String> allowed, String context) {
            for (String key : object.keySet()) {
                if (!allowed.contains(key)) {
                    throw new IllegalArgumentException("Unknown " + context + " field '" + key + "'");
                }
            }
        }
    }
}
