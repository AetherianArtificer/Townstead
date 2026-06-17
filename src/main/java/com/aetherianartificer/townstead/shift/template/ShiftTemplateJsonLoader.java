package com.aetherianartificer.townstead.shift.template;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads built-in shift templates from {@code data/<ns>/shift_templates/*.json}.
 * Each JSON has shape:
 * <pre>{@code
 * { "name_key": "townstead.shift.template.foo.name",
 *   "chronotype": "early_bird",    // optional
 *   "shifts": [..24 ints..] }
 * }</pre>
 */
public final class ShiftTemplateJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/ShiftTemplateJsonLoader");
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "shift_templates";

    public ShiftTemplateJsonLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        List<ShiftTemplate> parsed = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                TownsteadSchema.validate(obj, "townstead:shift_template/v1");
                String name;
                if (obj.has("name_key")) {
                    String key = GsonHelper.getAsString(obj, "name_key");
                    String resolved = Component.translatable(key).getString();
                    name = !resolved.equals(key) ? resolved : key;
                } else if (obj.has("name")) {
                    name = GsonHelper.getAsString(obj, "name");
                } else {
                    name = file.getPath();
                }
                Optional<String> chrono = Optional.empty();
                if (obj.has("chronotype")) {
                    String c = GsonHelper.getAsString(obj, "chronotype");
                    if (c != null && !c.isBlank()) chrono = Optional.of(c);
                }
                JsonArray arr = GsonHelper.getAsJsonArray(obj, "shifts");
                if (arr.size() != ShiftData.HOURS_PER_DAY) {
                    LOGGER.warn("Skipping {} — shifts must have {} entries (got {})",
                            file, ShiftData.HOURS_PER_DAY, arr.size());
                    continue;
                }
                int[] shifts = new int[ShiftData.HOURS_PER_DAY];
                boolean bad = false;
                for (int i = 0; i < ShiftData.HOURS_PER_DAY; i++) {
                    int v = arr.get(i).getAsInt();
                    if (v < 0 || v >= ShiftData.ORDINAL_TO_ACTIVITY.length) {
                        LOGGER.warn("Skipping {} — shift[{}] out of range: {}", file, i, v);
                        bad = true;
                        break;
                    }
                    shifts[i] = v;
                }
                if (bad) continue;
                parsed.add(new ShiftTemplate(file, name, shifts, chrono, true));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse shift template {}: {}", file, ex.getMessage());
            }
        }
        ShiftTemplateRegistry.setBuiltIns(parsed);
        LOGGER.info("Loaded {} built-in shift templates", parsed.size());
    }
}
