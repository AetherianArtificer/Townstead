package com.aetherianartificer.townstead.climate;

import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/**
 * A biome's climate from real data: its yearly mean, its coldest and warmest monthly means, and its
 * average daily range. The seasonal swing is half the gap between the months. Altitude cools the
 * air only above {@code typical_y}, the biome's usual surface height, so mountain biomes are not
 * cooled twice. {@code underground_offset_c} shifts the ground temperature wherever this biome lies
 * under cover, for cave biomes with a character of their own. Biomes without a profile use their zone.
 */
public record BiomeProfile(float mean, float coldestMonth, float warmestMonth, float dailyRange, int typicalY,
                           ClimateZone.SeasonKind seasonKind, float noSeasonFraction, float humidity,
                           float undergroundOffset) {
    public static final String SCHEMA = "townstead:climate_biome/v1";

    public float swing() {
        return Math.max(0f, (warmestMonth - coldestMonth) / 2f);
    }

    /**
     * The biome's climate shaped as a zone, keeping the id, humidity, wet/dry offsets and
     * no-season share of the zone it belongs to when the profile does not set them.
     */
    ClimateZone asZone(ResourceLocation id, ClimateZone zone) {
        float fraction = Float.isNaN(noSeasonFraction) ? (zone == null ? Float.NaN : zone.noSeasonFraction()) : noSeasonFraction;
        float wet = Float.isNaN(humidity) ? (zone == null ? 0.5f : zone.humidity()) : humidity;
        return new ClimateZone(id, seasonKind, seasonKind == ClimateZone.SeasonKind.NONE ? 0f : swing(), 1f / 12f,
                dailyRange / 2f, wet, 0f, zone == null ? 0f : zone.dryOffset(), zone == null ? 0f : zone.wetOffset(),
                fraction);
    }

    static BiomeProfile parse(JsonObject json) {
        TownsteadSchema.validate(json, SCHEMA);
        float mean = GsonHelper.getAsFloat(json, "mean_c");
        String kind = GsonHelper.getAsString(json, "season_kind", "four_seasons");
        ClimateZone.SeasonKind seasonKind;
        try {
            seasonKind = ClimateZone.SeasonKind.valueOf(kind.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown season_kind '" + kind + "'");
        }
        return new BiomeProfile(mean,
                GsonHelper.getAsFloat(json, "coldest_month_c", mean),
                GsonHelper.getAsFloat(json, "warmest_month_c", mean),
                Math.max(0f, GsonHelper.getAsFloat(json, "daily_range_c", 0f)),
                GsonHelper.getAsInt(json, "typical_y", Integer.MIN_VALUE),
                seasonKind,
                GsonHelper.getAsFloat(json, "no_season_swing_fraction", Float.NaN),
                json.has("humidity") ? Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(json, "humidity"))) : Float.NaN,
                GsonHelper.getAsFloat(json, "underground_offset_c", 0f));
    }
}
