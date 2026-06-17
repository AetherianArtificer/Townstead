package com.aetherianartificer.townstead.shift.weekplan;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.TownsteadSchema;
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

/**
 * Loads built-in week plans from {@code data/<ns>/week_plans/*.json}. Each JSON:
 * <pre>{@code
 * { "name_key": "townstead.weekplan.standard.name",
 *   "days": [ "townstead:work_day", "townstead:work_day", ..., "townstead:rest_day" ] }
 * }</pre>
 * Each {@code days} entry is a shift-template id; an empty string leaves that
 * day on the daily fallback.
 */
public final class WeekPlanJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/WeekPlanJsonLoader");
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "week_plans";

    public WeekPlanJsonLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        List<WeekPlan> parsed = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                TownsteadSchema.validate(obj, "townstead:week_plan/v1");
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
                JsonArray arr = GsonHelper.getAsJsonArray(obj, "days");
                List<String> days = new ArrayList<>(arr.size());
                for (JsonElement el : arr) {
                    days.add(el.isJsonNull() ? "" : el.getAsString());
                }
                parsed.add(new WeekPlan(file, name, days, true));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse week plan {}: {}", file, ex.getMessage());
            }
        }
        WeekPlanRegistry.setBuiltIns(parsed);
        LOGGER.info("Loaded {} built-in week plans", parsed.size());
    }
}
