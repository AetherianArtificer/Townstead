package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.Townstead;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@link CalendarProfile}s from
 * {@code data/<ns>/calendar_profile/*.json}. Each file shape:
 * <pre>{@code
 * {
 *   "type": "townstead:vanilla_math",
 *   "display_name": { "translate": "calendar.townstead.vanilla.name" },
 *   "days_per_week": 7,
 *   "months": [
 *     { "days": 30, "common_name": { "translate": "calendar.townstead.month.frostturn" } },
 *     ...
 *   ]
 * }
 * }</pre>
 *
 * {@code display_name} and {@code common_name} accept either a literal string
 * ({@code "Foo"}) or an object with a {@code translate} or {@code text} key,
 * mirroring MC's text component convention. Full {@code Component.Serializer}
 * isn't used because its signature drifts across 1.20.1 Forge and 1.21.1
 * NeoForge; the small parser here handles the cases data packs realistically need.
 */
public final class CalendarProfileJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/CalendarProfileJsonLoader");
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "calendar_profile";

    public CalendarProfileJsonLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, CalendarProfile> parsed = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                ResourceLocation typeId = parseResourceLocation(GsonHelper.getAsString(obj, "type"));
                if (CalendarTypes.byId(typeId) == null) {
                    LOGGER.warn("Skipping calendar profile {} — unknown type {}", file, typeId);
                    continue;
                }
                Component displayName = parseComponent(obj.get("display_name"), file.toString());
                int daysPerWeek = GsonHelper.getAsInt(obj, "days_per_week", 7);
                if (daysPerWeek <= 0) {
                    LOGGER.warn("Skipping calendar profile {} — days_per_week must be > 0", file);
                    continue;
                }
                JsonArray monthArr = GsonHelper.getAsJsonArray(obj, "months");
                List<MonthDef> months = new ArrayList<>(monthArr.size());
                boolean bad = false;
                for (int i = 0; i < monthArr.size(); i++) {
                    JsonObject mo = GsonHelper.convertToJsonObject(monthArr.get(i), file + ".months[" + i + "]");
                    int days = GsonHelper.getAsInt(mo, "days");
                    if (days <= 0) {
                        LOGGER.warn("Skipping {} — months[{}].days must be > 0", file, i);
                        bad = true;
                        break;
                    }
                    Component commonName = parseComponent(mo.get("common_name"), file + ".months[" + i + "]");
                    months.add(new MonthDef(commonName, days));
                }
                if (bad || months.isEmpty()) continue;
                parsed.put(file, new CalendarProfile(file, displayName, typeId, daysPerWeek, months));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse calendar profile {}: {}", file, ex.getMessage());
            }
        }
        CalendarProfileRegistry.replaceAll(parsed);
        LOGGER.info("Loaded {} calendar profiles", parsed.size());
    }

    private static Component parseComponent(JsonElement el, String context) {
        if (el == null || el.isJsonNull()) return Component.literal(context);
        if (el.isJsonPrimitive()) return Component.literal(el.getAsString());
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("translate")) return Component.translatable(obj.get("translate").getAsString());
            if (obj.has("text")) return Component.literal(obj.get("text").getAsString());
        }
        return Component.literal(context);
    }

    private static ResourceLocation parseResourceLocation(String s) {
        //? if >=1.21 {
        return ResourceLocation.parse(s);
        //?} else {
        /*return new ResourceLocation(s);
        *///?}
    }
}
