package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.calendar.WeekdayDef;
import com.aetherianartificer.townstead.clothing.ClothingQuery;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The calendar's day of the week, so a policy follows 5-, 7-, and 12-day weeks without edits.
 *
 * <p>JSON: {@code { "type":"pheno:weekday", "day": [0, 6] }} by zero-based index,
 * {@code { "type":"pheno:weekday", "name": "Sunday" }} by the profile's day name, or
 * {@code { "type":"pheno:weekday", "last": true }} for the week's final day. Names compare
 * case-insensitively against the profile's long and short names.</p>
 */
public final class WeekdayConditionType implements ConditionType {

    public static final String KEY = "pheno:weekday";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Set<Integer> days = new LinkedHashSet<>();
        JsonElement dayElement = json.get("day");
        if (dayElement != null) {
            if (dayElement.isJsonPrimitive()) days.add(dayElement.getAsInt());
            else if (dayElement.isJsonArray()) {
                for (JsonElement e : dayElement.getAsJsonArray()) {
                    if (e.isJsonPrimitive()) days.add(e.getAsInt());
                }
            }
        }
        Set<String> names = new LinkedHashSet<>();
        for (String name : ClothingQuery.strings(json.get("name"))) names.add(name.toLowerCase(Locale.ROOT));
        boolean last = GsonHelper.getAsBoolean(json, "last", false);
        boolean first = GsonHelper.getAsBoolean(json, "first", false);

        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel level)) return false;
            MinecraftServer server = level.getServer();
            CalendarDate today = TownsteadCalendar.today(server);
            if (today == null || today == CalendarDate.UNKNOWN) return false;
            int dow = today.dayOfWeek();
            if (days.contains(dow)) return true;
            CalendarProfile profile = TownsteadCalendar.activeProfile(server);
            int perWeek = profile == null ? 7 : profile.daysPerWeek();
            if (first && dow == 0) return true;
            if (last && dow == perWeek - 1) return true;
            if (!names.isEmpty() && profile != null && profile.weekdays() != null
                    && dow >= 0 && dow < profile.weekdays().size()) {
                WeekdayDef def = profile.weekdays().get(dow);
                if (def != null) {
                    String longName = def.longName() == null ? "" : def.longName().getString().toLowerCase(Locale.ROOT);
                    String shortName = def.shortName() == null ? "" : def.shortName().getString().toLowerCase(Locale.ROOT);
                    if (names.contains(longName) || names.contains(shortName)) return true;
                }
            }
            return false;
        };
    }
}
