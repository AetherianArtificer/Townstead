package com.aetherianartificer.townstead.climate;

import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/**
 * The shape of a place's climate: how far the year swings, how late it peaks, how far day and
 * night differ, and how humid it is. The mean comes from the biome; the zone only shapes it.
 */
public record ClimateZone(ResourceLocation id, SeasonKind seasonKind, float seasonalSwing, float peakLag,
                          float diurnalRange, float humidity, float meanOffset,
                          float dryOffset, float wetOffset) {
    public static final String SCHEMA = "townstead:climate_zone/v1";

    public enum SeasonKind { FOUR_SEASONS, WET_DRY, NONE }

    static ClimateZone parse(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validate(json, SCHEMA);
        String kind = GsonHelper.getAsString(json, "season_kind", "four_seasons");
        SeasonKind seasonKind;
        try {
            seasonKind = SeasonKind.valueOf(kind.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown season_kind '" + kind + "'");
        }
        JsonObject wetDry = GsonHelper.getAsJsonObject(json, "wet_dry", new JsonObject());
        return new ClimateZone(id, seasonKind,
                Math.max(0f, GsonHelper.getAsFloat(json, "seasonal_swing_c", 0f)),
                GsonHelper.getAsFloat(json, "peak_lag", 1f / 12f),
                Math.max(0f, GsonHelper.getAsFloat(json, "diurnal_range_c", 0f)),
                Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(json, "humidity", 0.5f))),
                GsonHelper.getAsFloat(json, "mean_offset_c", 0f),
                GsonHelper.getAsFloat(wetDry, "dry_offset_c", 0f),
                GsonHelper.getAsFloat(wetDry, "wet_offset_c", 0f));
    }
}
