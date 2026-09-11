package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.WeightedPool;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads name lists from {@code data/<ns>/name_list/<id>.json}.
 *
 * <pre>{@code
 * {
 *   "schema": "townstead:name_list/v1",
 *   "male":    ["Grunk", "Zogg"],
 *   "female":  { "Ashka": 3, "Vurr": 1 },
 *   "neutral": ["Sesh"],
 *   "names":   ["Ashmaw", "Emberhoof"]
 * }
 * }</pre>
 *
 * <p>Gendered keys hold given names; {@code names} holds ungendered family and clan names. A file
 * may carry either or both. A flat array weights every entry equally; an object maps name to
 * weight, applying the same square-root curve MCA uses on its own files so a list behaves the same
 * whichever mod an author copied from.</p>
 */
public final class NameListJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Townstead.MOD_ID + "/NameListJsonLoader");
    private static final Gson GSON = new Gson();
    private static final String SCHEMA = "townstead:name_list/v1";

    public NameListJsonLoader() {
        super(GSON, "name_list");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, NameList> loaded = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                TownsteadSchema.validate(root, SCHEMA);

                Map<Gender, WeightedPool<String>> given = NameLists.completeGiven(
                        pool(root, "male", file),
                        pool(root, "female", file),
                        pool(root, "neutral", file));
                WeightedPool<String> family = pool(root, "names", file);

                if (given.isEmpty() && family == null) {
                    LOGGER.warn("Name list {} holds no usable names, skipping", file);
                    continue;
                }
                loaded.put(file, new NameList(file, given, family));
            } catch (Exception exception) {
                LOGGER.warn("Could not load name list {}", file, exception);
            }
        }

        NameLists.replace(loaded);
        LOGGER.info("Loaded {} name list(s)", loaded.size());
    }

    /** Reads one weighted pool, or null when the key is absent or holds nothing usable. */
    static @Nullable WeightedPool<String> pool(JsonObject owner, String key, ResourceLocation file) {
        JsonElement element = owner.get(key);
        if (element == null) return null;

        WeightedPool.Mutable<String> names = new WeightedPool.Mutable<>("?");
        boolean any = false;

        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                if (value == null || !value.isJsonPrimitive()) continue;
                String name = value.getAsString();
                if (name == null || name.isBlank()) continue;
                names.add(name.trim(), 1.0F);
                any = true;
            }
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> weighted : element.getAsJsonObject().entrySet()) {
                String name = weighted.getKey();
                if (name == null || name.isBlank()) continue;
                int weight;
                try {
                    weight = weighted.getValue().getAsInt();
                } catch (Exception ignored) {
                    LOGGER.warn("Name list {} gave '{}' a non-numeric weight, ignoring it", file, name);
                    continue;
                }
                if (weight <= 0) continue;
                names.add(name.trim(), (float) Math.pow(weight, 0.5));
                any = true;
            }
        } else {
            LOGGER.warn("Name list {} field '{}' must be a list or an object of name to weight", file, key);
            return null;
        }

        return any ? names : null;
    }
}
