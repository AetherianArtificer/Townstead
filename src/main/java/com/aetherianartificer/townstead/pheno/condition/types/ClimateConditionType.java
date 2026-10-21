package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.calendar.SeasonClock;
import com.aetherianartificer.townstead.climate.ClimateModel;
import com.aetherianartificer.townstead.climate.ClimateZone;
import com.aetherianartificer.townstead.climate.ClimateZones;
import com.aetherianartificer.townstead.climate.WetDry;
import com.aetherianartificer.townstead.clothing.ClothingQuery;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.temperature.GroundClimate;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The climate where the entity stands. Every field is optional and all present fields must match.
 *
 * <p>JSON: {@code { "type":"pheno:climate", "zone":["arid","semi_arid"], "season_kind":"four_seasons",
 * "wet_dry":"dry", "year_phase":{"min":0.3,"max":0.5}, "snowing":false, "snow_cover":true,
 * "underground":false, "air_temperature":{"min":25} }}. Zone ids default to the {@code townstead}
 * namespace. {@code wet_dry} matches only in a wet/dry zone while the season mod reports that phase. A
 * {@code year_phase} with min above max wraps across the new year. Without a season mod,
 * {@code year_phase} never matches.</p>
 */
public final class ClimateConditionType implements ConditionType {
    public static final String KEY = "pheno:climate";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        List<ResourceLocation> zones = new ArrayList<>();
        if (json.has("zone")) {
            for (String raw : ClothingQuery.strings(json.get("zone"))) {
                ResourceLocation id = ResourceLocation.tryParse(raw.contains(":") ? raw : "townstead:" + raw);
                if (id != null) zones.add(id);
            }
        }
        ClimateZone.SeasonKind kind = null;
        if (json.has("season_kind")) {
            try {
                kind = ClimateZone.SeasonKind.valueOf(GsonHelper.getAsString(json, "season_kind").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // A misspelt kind is a condition that never fires, which the author can see.
                return ctx -> false;
            }
        }
        Boolean wantWet = null;
        if (json.has("wet_dry")) {
            String raw = GsonHelper.getAsString(json, "wet_dry").toLowerCase(Locale.ROOT);
            if (!raw.equals("wet") && !raw.equals("dry")) return ctx -> false;
            wantWet = raw.equals("wet");
        }
        Boolean wetDry = wantWet;
        double[] phase = range(json, "year_phase");
        double[] air = range(json, "air_temperature");
        Boolean snowing = json.has("snowing") ? GsonHelper.getAsBoolean(json, "snowing") : null;
        Boolean snowCover = json.has("snow_cover") ? GsonHelper.getAsBoolean(json, "snow_cover") : null;
        Boolean underground = json.has("underground") ? GsonHelper.getAsBoolean(json, "underground") : null;
        ClimateZone.SeasonKind wantedKind = kind;
        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel level)) return false;
            BlockPos pos = ctx.pos();
            if (pos == null) return false;
            if (!zones.isEmpty() || wantedKind != null || wetDry != null) {
                var zone = ClimateZones.of(level.getBiome(pos)).orElse(null);
                if (!zones.isEmpty() && (zone == null || !zones.contains(zone.id()))) return false;
                if (wantedKind != null && (zone == null || zone.seasonKind() != wantedKind)) return false;
                if (wetDry != null) {
                    if (zone == null || zone.seasonKind() != ClimateZone.SeasonKind.WET_DRY) return false;
                    var now = WetDry.current(SeasonClock.current(level.getServer()).orElse(null));
                    if (now.isEmpty() || now.get().wet() != wetDry) return false;
                }
            }
            if (phase != null) {
                var clock = SeasonClock.current(level.getServer());
                if (clock.isEmpty() || !inPhase(clock.get().phase(), phase[0], phase[1])) return false;
            }
            if (snowing != null || snowCover != null) {
                var snow = ClimateModel.snow(level, pos);
                if (snowing != null && snow.falling() != snowing) return false;
                if (snowCover != null && snow.lying() != snowCover) return false;
            }
            if (underground != null) {
                boolean below = GroundClimate.surfaceWeight(GroundClimate.probe(level, pos).depth()) <= 0f;
                if (below != underground) return false;
            }
            if (air != null) {
                float celsius = TemperatureData.worldCelsius(level, pos);
                if (celsius < air[0] || celsius > air[1]) return false;
            }
            return true;
        };
    }

    /** True inside [min, max]; a min above max wraps across the year boundary. */
    static boolean inPhase(double phase, double min, double max) {
        return min <= max ? phase >= min && phase <= max : phase >= min || phase <= max;
    }

    private static double[] range(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonObject()) return null;
        JsonObject range = json.getAsJsonObject(key);
        return new double[]{GsonHelper.getAsDouble(range, "min", Double.NEGATIVE_INFINITY),
                GsonHelper.getAsDouble(range, "max", Double.POSITIVE_INFINITY)};
    }
}
