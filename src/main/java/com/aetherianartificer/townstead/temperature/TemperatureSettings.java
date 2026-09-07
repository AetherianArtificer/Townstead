package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.Season;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tunables for the built-in ambient model, read from {@code data/townstead/need/temperature.json}
 * so a pack can retune the climate without Java. Missing fields keep the shipped defaults.
 */
public final class TemperatureSettings {
    public static final String SCHEMA = "townstead:need_settings/v1";
    private static final String PATH = "need/temperature.json";
    private static volatile TemperatureSettings CURRENT = new TemperatureSettings();

    private float[] anchorBiome = {0.15f, 0.8f, 2.0f};
    private float[] anchorCelsius = {0f, 20f, 38f};
    private final Map<Season, Float> seasonOffsets = new EnumMap<>(Season.class);
    private float altitudeBlocksPerDegree = 15f;
    private float rainOffset = -5f;
    private float nightOffset = -6f;
    private float netherCelsius = 45f;
    private float endCelsius = 5f;
    private int sourceRadius = 7;
    private float heatSourceOffset = 12f;
    private float coolingSourceOffset = -8f;
    private float roomBaseline = 18f;
    private float insulationMax = 0.6f;
    private float indoorNightFactor = 0.25f;
    private float occupancyPerVillager = 0.4f;
    private float occupancyMax = 3f;
    private int roomSizeReference = 64;
    private boolean roomHeatEnabled = true;
    private float roomHeatCapacity = 2f, roomSourcePower = 1.5f;
    private float roomWallConductance = 0.07f, roomInsulatedConductance = 0.004f, roomOpeningConductance = 1f;
    public boolean roomHeatEnabled() { return roomHeatEnabled; }
    public float roomHeatCapacity() { return roomHeatCapacity; }
    public float roomSourcePower() { return roomSourcePower; }
    public float roomWallConductance() { return roomWallConductance; }
    public float roomInsulatedConductance() { return roomInsulatedConductance; }
    public float roomOpeningConductance() { return roomOpeningConductance; }
    private float dryingSeconds = 90;
    private float comfortBreakSeconds = 30;

    private TemperatureSettings() {
        seasonOffsets.put(Season.SPRING, 0f);
        seasonOffsets.put(Season.SUMMER, 6f);
        seasonOffsets.put(Season.AUTUMN, -2f);
        seasonOffsets.put(Season.WINTER, -12f);
    }

    public static TemperatureSettings get() {
        return CURRENT;
    }

    /** Vanilla biome temperature mapped piecewise-linearly through the anchors, extrapolating past the ends. */
    public float biomeToCelsius(float biomeTemperature) {
        int n = anchorBiome.length;
        if (n == 0) return 20f;
        if (n == 1) return anchorCelsius[0];
        int i = 0;
        while (i < n - 2 && biomeTemperature > anchorBiome[i + 1]) i++;
        float b0 = anchorBiome[i], b1 = anchorBiome[i + 1];
        float c0 = anchorCelsius[i], c1 = anchorCelsius[i + 1];
        if (b1 == b0) return c0;
        return c0 + (biomeTemperature - b0) * (c1 - c0) / (b1 - b0);
    }

    public float seasonOffset(Season season) {
        Float value = seasonOffsets.get(season);
        return value == null ? 0f : value;
    }

    public float altitudeBlocksPerDegree() { return altitudeBlocksPerDegree; }
    public float rainOffset() { return rainOffset; }
    public float nightOffset() { return nightOffset; }
    public float netherCelsius() { return netherCelsius; }
    public float endCelsius() { return endCelsius; }
    public int sourceRadius() { return sourceRadius; }
    public float heatSourceOffset() { return heatSourceOffset; }
    public float coolingSourceOffset() { return coolingSourceOffset; }
    /** The mild indoor temperature a fully insulated room is pulled toward. */
    public float roomBaseline() { return roomBaseline; }
    /** How far a room whose shell is entirely insulating blocks moves toward the baseline (0..1). */
    public float insulationMax() { return insulationMax; }
    public float indoorNightFactor() { return indoorNightFactor; }
    public float occupancyPerVillager() { return occupancyPerVillager; }
    public float occupancyMax() { return occupancyMax; }
    /** Interior air volume at which one hearth heats a room by its full offset. */
    public int roomSizeReference() { return roomSizeReference; }
    public float dryingSeconds() { return dryingSeconds; }
    public float comfortBreakSeconds() { return comfortBreakSeconds; }

    static TemperatureSettings parse(JsonObject json) {
        TemperatureSettings s = new TemperatureSettings();
        s.dryingSeconds = Math.max(1, GsonHelper.getAsFloat(json, "drying_seconds", 90));
        s.comfortBreakSeconds = Math.max(1, GsonHelper.getAsFloat(json, "comfort_break_seconds", 30));
        if (json.has("biome_anchors") && json.get("biome_anchors").isJsonArray()) {
            JsonArray anchors = json.getAsJsonArray("biome_anchors");
            float[] biome = new float[anchors.size()];
            float[] celsius = new float[anchors.size()];
            int count = 0;
            for (JsonElement element : anchors) {
                if (!element.isJsonArray() || element.getAsJsonArray().size() < 2) continue;
                JsonArray pair = element.getAsJsonArray();
                biome[count] = pair.get(0).getAsFloat();
                celsius[count] = pair.get(1).getAsFloat();
                count++;
            }
            if (count >= 2) {
                s.anchorBiome = java.util.Arrays.copyOf(biome, count);
                s.anchorCelsius = java.util.Arrays.copyOf(celsius, count);
            }
        }
        if (json.has("season_offsets") && json.get("season_offsets").isJsonObject()) {
            JsonObject offsets = json.getAsJsonObject("season_offsets");
            for (Season season : Season.values()) {
                String key = season.name().toLowerCase(java.util.Locale.ROOT);
                if (offsets.has(key)) s.seasonOffsets.put(season, GsonHelper.getAsFloat(offsets, key));
            }
        }
        s.altitudeBlocksPerDegree = Math.max(1f, GsonHelper.getAsFloat(json, "altitude_blocks_per_degree", s.altitudeBlocksPerDegree));
        s.rainOffset = GsonHelper.getAsFloat(json, "rain_offset", s.rainOffset);
        s.nightOffset = GsonHelper.getAsFloat(json, "night_offset", s.nightOffset);
        s.netherCelsius = GsonHelper.getAsFloat(json, "nether_celsius", s.netherCelsius);
        s.endCelsius = GsonHelper.getAsFloat(json, "end_celsius", s.endCelsius);
        s.sourceRadius = Math.max(0, Math.min(12, GsonHelper.getAsInt(json, "source_radius", s.sourceRadius)));
        s.heatSourceOffset = GsonHelper.getAsFloat(json, "heat_source_offset", s.heatSourceOffset);
        s.coolingSourceOffset = GsonHelper.getAsFloat(json, "cooling_source_offset", s.coolingSourceOffset);
        s.roomBaseline = GsonHelper.getAsFloat(json, "room_baseline", s.roomBaseline);
        s.insulationMax = Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(json, "insulation_max", s.insulationMax)));
        s.indoorNightFactor = Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(json, "indoor_night_factor", s.indoorNightFactor)));
        s.occupancyPerVillager = GsonHelper.getAsFloat(json, "occupancy_per_villager", s.occupancyPerVillager);
        s.occupancyMax = GsonHelper.getAsFloat(json, "occupancy_max", s.occupancyMax);
        s.roomSizeReference = Math.max(1, GsonHelper.getAsInt(json, "room_size_reference", s.roomSizeReference));
        s.roomHeatEnabled = GsonHelper.getAsBoolean(json, "room_heat_enabled", true);
        s.roomHeatCapacity = positive(json, "room_heat_capacity", s.roomHeatCapacity);
        s.roomSourcePower = positive(json, "room_source_power", s.roomSourcePower);
        s.roomWallConductance = positive(json, "room_wall_conductance", s.roomWallConductance);
        s.roomInsulatedConductance = positive(json, "room_insulated_conductance", s.roomInsulatedConductance);
        s.roomOpeningConductance = positive(json, "room_opening_conductance", s.roomOpeningConductance);
        return s;
    }

    private static float positive(JsonObject json, String key, float fallback) {
        float value = GsonHelper.getAsFloat(json, key, fallback);
        return Float.isFinite(value) && value > 0 ? value : fallback;
    }

    public static final class Loader extends SimplePreparableReloadListener<Optional<JsonObject>> {
        @Override
        protected Optional<JsonObject> prepare(ResourceManager manager, ProfilerFiller profiler) {
            //? if >=1.21 {
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, PATH);
            //?} else {
            /*ResourceLocation file = new ResourceLocation(Townstead.MOD_ID, PATH);
            *///?}
            Optional<Resource> resource = manager.getResource(file);
            if (resource.isEmpty()) return Optional.empty();
            try (Reader reader = resource.get().openAsReader()) {
                JsonElement parsed = JsonParser.parseReader(reader);
                return parsed.isJsonObject() ? Optional.of(parsed.getAsJsonObject()) : Optional.empty();
            } catch (Exception e) {
                Townstead.LOGGER.warn("Failed to read {}: {}", file, e.getMessage());
                return Optional.empty();
            }
        }

        @Override
        protected void apply(Optional<JsonObject> prepared, ResourceManager manager, ProfilerFiller profiler) {
            BuildingClimate.clear();
            com.aetherianartificer.townstead.compat.temperature.ColdSweatTemperatureBridge.INSTANCE.clearCache();
            if (prepared.isEmpty()) {
                CURRENT = new TemperatureSettings();
                return;
            }
            try {
                CURRENT = parse(prepared.get());
            } catch (RuntimeException e) {
                Townstead.LOGGER.warn("Temperature settings rejected, keeping defaults: {}", e.getMessage());
                CURRENT = new TemperatureSettings();
            }
        }
    }
}
