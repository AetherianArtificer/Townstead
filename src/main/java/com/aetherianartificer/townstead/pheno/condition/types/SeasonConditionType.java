package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.Season;
import com.aetherianartificer.townstead.calendar.SeasonClock;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.climate.ClimateZone;
import com.aetherianartificer.townstead.climate.ClimateZones;
import com.aetherianartificer.townstead.climate.WetDry;
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
 * no season and nothing matches. {@code wet} and {@code dry} match the season mod's wet/dry phase
 * where the entity stands in a wet/dry climate zone.
 *
 * <p>JSON: {@code { "type":"pheno:season", "season": ["autumn", "winter", "dry"] }}.</p>
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
        boolean wantWet = false, wantDry = false;
        for (String raw : ClothingQuery.strings(json.get("season"))) {
            String id = raw.trim().toLowerCase(Locale.ROOT);
            if (id.equals("wet")) { wantWet = true; continue; }
            if (id.equals("dry")) { wantDry = true; continue; }
            try {
                wanted.add(Season.valueOf(id.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // A misspelt season is a condition that never fires, which the author can see.
            }
        }
        boolean wet = wantWet, dry = wantDry;
        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel level)) return false;
            if (!wanted.isEmpty()) {
                CalendarDate today = TownsteadCalendar.today(level.getServer());
                if (today != null && today.season() != null && wanted.contains(today.season())) return true;
            }
            if ((wet || dry) && ctx.pos() != null) {
                var zone = ClimateZones.of(level.getBiome(ctx.pos())).orElse(null);
                if (zone == null || zone.seasonKind() != ClimateZone.SeasonKind.WET_DRY) return false;
                var phase = WetDry.current(SeasonClock.current(level.getServer()).orElse(null));
                return phase.isPresent() && (phase.get().wet() ? wet : dry);
            }
            return false;
        };
    }
}
