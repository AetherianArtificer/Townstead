package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.Season;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.clothing.ClothingQuery;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * The calendar's season, from whichever seasonal mod drives the calendar. Without one there is
 * no season and nothing matches.
 *
 * <p>JSON: {@code { "type":"pheno:season", "season": ["autumn", "winter"] }}.</p>
 */
public final class SeasonConditionType implements ConditionType {

    public static final String KEY = "pheno:season";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Set<Season> wanted = EnumSet.noneOf(Season.class);
        for (String raw : ClothingQuery.strings(json.get("season"))) {
            try {
                wanted.add(Season.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // A misspelt season is a condition that never fires, which the author can see.
            }
        }
        return ctx -> {
            if (wanted.isEmpty() || !(ctx.level() instanceof ServerLevel level)) return false;
            CalendarDate today = TownsteadCalendar.today(level.getServer());
            return today != null && today.season() != null && wanted.contains(today.season());
        };
    }
}
