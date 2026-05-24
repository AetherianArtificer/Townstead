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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@link CalendarProfile}s from
 * {@code data/<ns>/calendar_profile/<path>.json}. Each file shape:
 * <pre>{@code
 * {
 *   "display_name": { "translate": "calendar_profile.townstead_calendar.default.name" },
 *   "days_per_week": 7,
 *   "months": [
 *     { "days": 31, "common_name": { "translate": "calendar_profile.townstead_calendar.default.month.january" } },
 *     ...
 *   ]
 * }
 * }</pre>
 *
 * {@code display_name} and {@code common_name} accept any of:
 * <ul>
 *   <li>literal string: {@code "January"}</li>
 *   <li>{@code { "text": "January" }}</li>
 *   <li>{@code { "translate": "calendar_profile.foo.bar.month.january" }}</li>
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
 * <p>The mod's own bundled profiles use the same sidecar mechanism: their
 * {@code calendar_profile.*} strings live in
 * {@code data/townstead_calendar/lang/en_us.json}, alongside the JSONs they
 * describe. Pure client-side strings (GUI titles, date-format patterns) stay
 * in {@code assets/townstead_calendar/lang/en_us.json}.</p>
 *
 * <p><b>Optional per-profile date format override.</b> A profile may include
 * a {@code formats} object whose keys are style names ({@code long},
 * {@code medium}, {@code short}, {@code with_weekday}) and values are
 * translate-key Components carrying a format pattern. Any style omitted from
 * the map falls back to the global
 * {@code townstead.calendar.format.<style>} key.</p>
 *
 * <p><b>Optional leap rules.</b> A profile may include a {@code leap_rules}
 * array. Each entry has a {@code when} predicate (modular arithmetic on the
 * year number) and one action key:
 * <ul>
 *   <li>{@code add_day_to_month: <int>} — extend month at 1-based base index by 1 day</li>
 *   <li>{@code subtract_day_from_month: <int>} — shrink by 1 day</li>
 *   <li>{@code insert_month_after: <int>} + {@code month: { days, common_name }} — insert after base index</li>
 *   <li>{@code insert_month_at_end: true} + {@code month: { ... }} — append</li>
 * </ul>
 * Predicate forms:
 * <pre>{@code
 *   { "year_mod": 4, "equals": 0 }
 *   { "year_mod": 19, "in": [3, 6, 8, 11, 14, 17, 0] }
 *   { "all_of": [ <predicate>, <predicate>, ... ] }
 *   { "any_of": [ <predicate>, <predicate>, ... ] }
 * }</pre>
 * Rules apply top-to-bottom and all matching rules contribute. Gregorian's
 * 4/100/400 cascade is three rules with deltas +1 / -1 / +1.</p>
 *
 * <p><b>Named placeholders in pattern strings.</b> When a sidecar lang value
 * contains tokens like {@code {day}} or {@code {month}}, the loader rewrites
 * them to MC's positional format args before constructing the Component. Pack
 * authors can write
 * {@code "{weekday}, {day} of {month}, {era} {year}"} instead of
 * {@code "%4$s, %1$s of %2$s, %5$s %3$s"}. Supported tokens:
 * <ul>
 *   <li>{@code {day}} → {@code %1$s} day-of-month</li>
 *   <li>{@code {month}} → {@code %2$s} month name</li>
 *   <li>{@code {year}} → {@code %3$s} display year</li>
 *   <li>{@code {weekday}} → {@code %4$s} weekday name (WITH_WEEKDAY only)</li>
 *   <li>{@code {era}} → {@code %5$s} era abbreviation / year suffix</li>
 *   <li>{@code {month_index}} → {@code %6$s} month index (numeric)</li>
 * </ul>
 * Already-positional patterns pass through unchanged.</p>
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
        Map<String, String> langIndex = com.aetherianartificer.townstead.data.DataPackLang
                .loadLangIndex(resourceManager, CalendarProfileJsonLoader::convertNamedPlaceholders);
        Map<ResourceLocation, CalendarProfile> parsed = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
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

                Map<CalendarDateFormatter.Style, Component> formats = null;
                if (obj.has("formats")) {
                    JsonObject fmtObj = GsonHelper.getAsJsonObject(obj, "formats");
                    formats = new EnumMap<>(CalendarDateFormatter.Style.class);
                    for (Map.Entry<String, JsonElement> fe : fmtObj.entrySet()) {
                        CalendarDateFormatter.Style style = CalendarDateFormatter.Style.byJsonKey(fe.getKey());
                        if (style == null) {
                            LOGGER.warn("Skipping {} — formats has unknown style {}", file, fe.getKey());
                            continue;
                        }
                        formats.put(style, parseComponent(fe.getValue(),
                                file + ".formats." + fe.getKey(), langIndex));
                    }
                    if (formats.isEmpty()) formats = null;
                }

                List<LeapRule> leapRules = null;
                if (obj.has("leap_rules")) {
                    JsonArray lrArr = GsonHelper.getAsJsonArray(obj, "leap_rules");
                    leapRules = new ArrayList<>(lrArr.size());
                    boolean badLeap = false;
                    for (int i = 0; i < lrArr.size(); i++) {
                        JsonObject lo = GsonHelper.convertToJsonObject(lrArr.get(i),
                                file + ".leap_rules[" + i + "]");
                        LeapRule.Predicate pred;
                        try {
                            pred = parsePredicate(GsonHelper.getAsJsonObject(lo, "when"),
                                    file + ".leap_rules[" + i + "].when");
                        } catch (Exception ex) {
                            LOGGER.warn("Skipping {} — leap_rules[{}].when invalid: {}", file, i, ex.getMessage());
                            badLeap = true; break;
                        }
                        LeapRule.Action act;
                        try {
                            act = parseLeapAction(lo, file + ".leap_rules[" + i + "]", months.size(), langIndex);
                        } catch (Exception ex) {
                            LOGGER.warn("Skipping {} — leap_rules[{}] action invalid: {}", file, i, ex.getMessage());
                            badLeap = true; break;
                        }
                        leapRules.add(new LeapRule(pred, act));
                    }
                    if (badLeap) continue;
                    if (leapRules.isEmpty()) leapRules = null;
                }

                parsed.put(file, new CalendarProfile(file, displayName, daysPerWeek, months, yearSuffix, weekdays, eras, formats, leapRules));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse calendar profile {}: {}", file, ex.getMessage());
            }
        }
        CalendarProfileRegistry.replaceAll(parsed);
        LeapEngine.clearCache();
        LOGGER.info("Loaded {} calendar profiles ({} sidecar lang entries)", parsed.size(), langIndex.size());
    }

    /**
     * Parse a {@code when} predicate object. Recursive: {@code all_of} and
     * {@code any_of} take arrays of nested predicate objects.
     */
    private static LeapRule.Predicate parsePredicate(JsonObject obj, String context) {
        if (obj.has("all_of")) {
            JsonArray arr = GsonHelper.getAsJsonArray(obj, "all_of");
            List<LeapRule.Predicate> parts = new ArrayList<>(arr.size());
            for (int i = 0; i < arr.size(); i++) {
                parts.add(parsePredicate(GsonHelper.convertToJsonObject(arr.get(i), context + ".all_of[" + i + "]"),
                        context + ".all_of[" + i + "]"));
            }
            return new LeapRule.AllOf(parts);
        }
        if (obj.has("any_of")) {
            JsonArray arr = GsonHelper.getAsJsonArray(obj, "any_of");
            List<LeapRule.Predicate> parts = new ArrayList<>(arr.size());
            for (int i = 0; i < arr.size(); i++) {
                parts.add(parsePredicate(GsonHelper.convertToJsonObject(arr.get(i), context + ".any_of[" + i + "]"),
                        context + ".any_of[" + i + "]"));
            }
            return new LeapRule.AnyOf(parts);
        }
        if (obj.has("year_mod")) {
            int mod = GsonHelper.getAsInt(obj, "year_mod");
            if (obj.has("in")) {
                JsonArray arr = GsonHelper.getAsJsonArray(obj, "in");
                int[] residues = new int[arr.size()];
                for (int i = 0; i < arr.size(); i++) residues[i] = arr.get(i).getAsInt();
                return new LeapRule.In(mod, residues);
            }
            if (obj.has("equals")) {
                return new LeapRule.Equals(mod, GsonHelper.getAsInt(obj, "equals"));
            }
            throw new IllegalArgumentException(context + " — year_mod requires 'equals' or 'in'");
        }
        throw new IllegalArgumentException(context + " — predicate must have 'year_mod', 'all_of', or 'any_of'");
    }

    /**
     * Parse the action half of a leap rule. Exactly one action key must
     * appear ({@code add_day_to_month}, {@code subtract_day_from_month},
     * {@code insert_month_after}, or {@code insert_month_at_end}).
     */
    private static LeapRule.Action parseLeapAction(JsonObject lo, String context, int baseMonthCount,
                                                   Map<String, String> langIndex) {
        if (lo.has("add_day_to_month")) {
            int idx = GsonHelper.getAsInt(lo, "add_day_to_month");
            validateBaseMonthIndex(idx, baseMonthCount, context, "add_day_to_month");
            return new LeapRule.AdjustDays(idx, +1);
        }
        if (lo.has("subtract_day_from_month")) {
            int idx = GsonHelper.getAsInt(lo, "subtract_day_from_month");
            validateBaseMonthIndex(idx, baseMonthCount, context, "subtract_day_from_month");
            return new LeapRule.AdjustDays(idx, -1);
        }
        if (lo.has("rename_month")) {
            int idx = GsonHelper.getAsInt(lo, "rename_month");
            validateBaseMonthIndex(idx, baseMonthCount, context, "rename_month");
            if (!lo.has("name")) {
                throw new IllegalArgumentException(context + " — rename_month requires 'name'");
            }
            Component newName = parseComponent(lo.get("name"), context + ".name", langIndex);
            return new LeapRule.RenameMonth(idx, newName);
        }
        if (lo.has("insert_month_at_end") && lo.get("insert_month_at_end").getAsBoolean()) {
            MonthDef m = parseInsertedMonth(lo, context, langIndex);
            return new LeapRule.InsertMonth(null, m);
        }
        if (lo.has("insert_month_after")) {
            int afterIdx = GsonHelper.getAsInt(lo, "insert_month_after");
            if (afterIdx < 0 || afterIdx > baseMonthCount) {
                throw new IllegalArgumentException(context + " — insert_month_after out of range: " + afterIdx);
            }
            MonthDef m = parseInsertedMonth(lo, context, langIndex);
            return new LeapRule.InsertMonth(afterIdx, m);
        }
        throw new IllegalArgumentException(context + " — must have add_day_to_month, subtract_day_from_month, rename_month, insert_month_after, or insert_month_at_end");
    }

    private static void validateBaseMonthIndex(int idx, int baseMonthCount, String context, String key) {
        if (idx < 1 || idx > baseMonthCount) {
            throw new IllegalArgumentException(context + " — " + key + " out of range: " + idx
                    + " (profile has " + baseMonthCount + " base months)");
        }
    }

    private static MonthDef parseInsertedMonth(JsonObject lo, String context, Map<String, String> langIndex) {
        if (!lo.has("month")) {
            throw new IllegalArgumentException(context + " — insertion action missing 'month' object");
        }
        JsonObject mo = GsonHelper.getAsJsonObject(lo, "month");
        int days = GsonHelper.getAsInt(mo, "days");
        if (days <= 0) throw new IllegalArgumentException(context + ".month.days must be > 0");
        Component name = parseComponent(mo.get("common_name"), context + ".month", langIndex);
        return new MonthDef(name, days);
    }

    /**
     * Rewrite named placeholders to MC's positional format args. Idempotent on
     * already-positional patterns. See class Javadoc for the supported token
     * vocabulary.
     */
    static String convertNamedPlaceholders(String s) {
        if (s == null || s.indexOf('{') < 0) return s;
        return s
                .replace("{day}",         "%1$s")
                .replace("{month}",       "%2$s")
                .replace("{year}",        "%3$s")
                .replace("{weekday}",     "%4$s")
                .replace("{era}",         "%5$s")
                .replace("{month_index}", "%6$s");
    }

    private static Component parseComponent(JsonElement el, String context, Map<String, String> langIndex) {
        return com.aetherianartificer.townstead.data.DataPackLang.parseComponent(el, context, langIndex);
    }
}
