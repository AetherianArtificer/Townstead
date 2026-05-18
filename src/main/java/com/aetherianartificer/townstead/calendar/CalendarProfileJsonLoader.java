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

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@link CalendarProfile}s from
 * {@code data/<ns>/calendar_profile/<path>.json}. Each file shape:
 * <pre>{@code
 * {
 *   "type": "townstead:vanilla_math",
 *   "display_name": { "translate": "calendar_profile.townstead_calendar.default.name" },
 *   "days_per_week": 7,
 *   "months": [
 *     { "days": 31, "common_name": { "translate": "calendar_profile.townstead_calendar.default.month.axolen" } },
 *     ...
 *   ]
 * }
 * }</pre>
 *
 * {@code display_name} and {@code common_name} accept any of:
 * <ul>
 *   <li>literal string: {@code "Axolen"}</li>
 *   <li>{@code { "text": "Axolen" }}</li>
 *   <li>{@code { "translate": "calendar_profile.foo.bar.month.axolen" }}</li>
 * </ul>
 *
 * Recommended lang key convention mirrors the profile's resource path so the
 * key is a deterministic transform of the id:
 * {@code <namespace>:<path>} → {@code calendar_profile.<namespace>.<path-with-dots>}.
 * For months inside a profile: append {@code .month.<short_name>}.
 *
 * <p><b>Lang sidecars (data-pack-distributed profiles).</b> Calendar profiles
 * are routinely shipped as data packs to servers, but {@code assets/} resource
 * pack content does not reach the server. To make data-pack-distributed
 * profiles render correctly on every client without requiring a paired
 * resource pack install, this loader also scans
 * {@code data/<ns>/lang/en_us.json} (Townstead-private convention; same JSON
 * format as a standard MC lang file). Any translate key it can resolve in
 * that sidecar gets stuffed into the Component's fallback slot, which the
 * existing sync packet carries to clients. Resource packs can still override
 * the translate key for non-English locales — the sidecar only populates the
 * fallback that shows when no client-side translation is available.</p>
 *
 * <p>The mod's own bundled profiles do not need a sidecar because the mod jar
 * itself ships an {@code assets/<ns>/lang/en_us.json} that every client has;
 * the sidecar mechanism is additive for packs that don't have that guarantee.</p>
 */
public final class CalendarProfileJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/CalendarProfileJsonLoader");
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "calendar_profile";
    private static final String LANG_SIDECAR_PATH = "lang/en_us.json";

    public CalendarProfileJsonLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, String> langIndex = loadLangIndex(resourceManager);
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
                Component displayName = parseComponent(obj.get("display_name"), file.toString(), langIndex);
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
                    Component commonName = parseComponent(mo.get("common_name"), file + ".months[" + i + "]", langIndex);
                    months.add(new MonthDef(commonName, days));
                }
                if (bad || months.isEmpty()) continue;
                Component yearSuffix = obj.has("year_suffix")
                        ? parseComponent(obj.get("year_suffix"), file + ".year_suffix", langIndex)
                        : null;

                List<WeekdayDef> weekdays = null;
                if (obj.has("weekdays")) {
                    JsonArray wdArr = GsonHelper.getAsJsonArray(obj, "weekdays");
                    if (wdArr.size() != daysPerWeek) {
                        LOGGER.warn("Skipping {} — weekdays length {} != days_per_week {}",
                                file, wdArr.size(), daysPerWeek);
                        continue;
                    }
                    weekdays = new ArrayList<>(wdArr.size());
                    for (int i = 0; i < wdArr.size(); i++) {
                        JsonObject wo = GsonHelper.convertToJsonObject(wdArr.get(i),
                                file + ".weekdays[" + i + "]");
                        Component longName = parseComponent(wo.get("long"),
                                file + ".weekdays[" + i + "].long", langIndex);
                        Component shortName = wo.has("short")
                                ? parseComponent(wo.get("short"), file + ".weekdays[" + i + "].short", langIndex)
                                : longName;
                        weekdays.add(new WeekdayDef(longName, shortName));
                    }
                }

                List<Era> eras = null;
                if (obj.has("eras")) {
                    JsonArray eraArr = GsonHelper.getAsJsonArray(obj, "eras");
                    eras = new ArrayList<>(eraArr.size());
                    for (int i = 0; i < eraArr.size(); i++) {
                        JsonObject eo = GsonHelper.convertToJsonObject(eraArr.get(i),
                                file + ".eras[" + i + "]");
                        Component name = parseComponent(eo.get("name"),
                                file + ".eras[" + i + "].name", langIndex);
                        int startYear = GsonHelper.getAsInt(eo, "start_year");
                        int firstYearDisplayedAs = GsonHelper.getAsInt(eo, "first_year_displayed_as", 1);
                        String dirStr = GsonHelper.getAsString(eo, "direction", "ascending");
                        Era.Direction direction = "descending".equalsIgnoreCase(dirStr)
                                ? Era.Direction.DESCENDING
                                : Era.Direction.ASCENDING;
                        eras.add(new Era(name, startYear, firstYearDisplayedAs, direction));
                    }
                }

                parsed.put(file, new CalendarProfile(file, displayName, typeId, daysPerWeek, months, yearSuffix, weekdays, eras));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse calendar profile {}: {}", file, ex.getMessage());
            }
        }
        CalendarProfileRegistry.replaceAll(parsed);
        LOGGER.info("Loaded {} calendar profiles ({} sidecar lang entries)", parsed.size(), langIndex.size());
    }

    /**
     * Scan every namespace for {@code data/<ns>/lang/en_us.json} and merge the
     * (key, English-string) entries into one map. Same JSON shape as a standard
     * Minecraft lang file. Silently skips namespaces without a sidecar and
     * malformed files (warns).
     */
    private static Map<String, String> loadLangIndex(ResourceManager rm) {
        Map<String, String> out = new HashMap<>();
        for (String ns : rm.getNamespaces()) {
            ResourceLocation loc = parseResourceLocation(ns + ":" + LANG_SIDECAR_PATH);
            rm.getResource(loc).ifPresent(res -> {
                try (Reader r = res.openAsReader()) {
                    JsonElement el = GSON.fromJson(r, JsonElement.class);
                    if (el == null || !el.isJsonObject()) return;
                    JsonObject obj = el.getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                        JsonElement v = e.getValue();
                        if (v != null && v.isJsonPrimitive()) {
                            out.put(e.getKey(), v.getAsString());
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.warn("Failed to read calendar lang sidecar {}: {}", loc, ex.getMessage());
                }
            });
        }
        return out;
    }

    private static Component parseComponent(JsonElement el, String context, Map<String, String> langIndex) {
        if (el == null || el.isJsonNull()) return Component.literal(context);
        if (el.isJsonPrimitive()) return Component.literal(el.getAsString());
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("translate")) {
                String key = obj.get("translate").getAsString();
                String resolved = langIndex.get(key);
                if (resolved != null) {
                    return Component.translatableWithFallback(key, resolved);
                }
                return Component.translatable(key);
            }
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
