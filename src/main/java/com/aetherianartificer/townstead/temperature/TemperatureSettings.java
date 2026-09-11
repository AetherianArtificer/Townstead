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
    // Effective air + contents mass, source calibration, and envelope transfer in explicit units.
    private float roomHeatCapacity = 2000f, roomSourcePower = 250f;
    private float roomWallConductance = 1.4f, roomInsulatedConductance = 0.08f, roomOpeningConductance = 30f;
    private float thermalTimeScale = 20f, airChangesPerHour = 0.5f;
    private float cookingRoomHeatFraction = 0.15f;
    private final Map<com.aetherianartificer.townstead.temperature.ThermalConductance.Material, Float> materialCapacity =
            new EnumMap<>(com.aetherianartificer.townstead.temperature.ThermalConductance.Material.class);
    private final Map<String, ThermalAppliance> appliances = new java.util.LinkedHashMap<>();
    public ThermalAppliance appliance(String id) { return appliances.get(id); }
    public float materialHeatCapacity(ThermalConductance.Material material) { return materialCapacity.get(material); }
    public float cookingRoomHeatFraction() { return cookingRoomHeatFraction; }
    public float thermalTimeScale() { return thermalTimeScale; }
    public float airChangesPerHour() { return airChangesPerHour; }
    public boolean roomHeatEnabled() { return roomHeatEnabled; }
    public float roomHeatCapacity() { return roomHeatCapacity; }
    public float roomSourcePower() { return roomSourcePower; }
    public float roomWallConductance() { return roomWallConductance; }
    public float roomInsulatedConductance() { return roomInsulatedConductance; }
    public float roomOpeningConductance() { return roomOpeningConductance; }
    private float dryingSeconds = 90;
    private float comfortBreakSeconds = 90;

    private TemperatureSettings() {
        appliances.put("legendarysurvivaloverhaul:heater", new ThermalAppliance(3750, 15, 15));
        appliances.put("legendarysurvivaloverhaul:cooler", new ThermalAppliance(-3750, -15, -15));
        appliances.put("legendarysurvivaloverhaul:heater_top", new ThermalAppliance(0, 0, 0));
        appliances.put("minecraft:campfire", new ThermalAppliance(2500, 10, 0));
        appliances.put("minecraft:soul_campfire", new ThermalAppliance(-2000, -8, 0));
        appliances.put("minecraft:furnace", new ThermalAppliance(300, 8, 0));
        appliances.put("minecraft:smoker", new ThermalAppliance(300, 8, 0));
        appliances.put("minecraft:blast_furnace", new ThermalAppliance(450, 12, 0));
        materialCapacity.put(ThermalConductance.Material.INSULATION, 500f);
        materialCapacity.put(ThermalConductance.Material.WOOD, 4000f);
        materialCapacity.put(ThermalConductance.Material.EARTH, 16000f);
        materialCapacity.put(ThermalConductance.Material.MASONRY, 12000f);
        materialCapacity.put(ThermalConductance.Material.GLASS, 6000f);
        materialCapacity.put(ThermalConductance.Material.METAL, 16000f);
        materialCapacity.put(ThermalConductance.Material.POROUS, 1000f);
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
        s.comfortBreakSeconds = Math.max(1, GsonHelper.getAsFloat(json, "comfort_break_seconds", s.comfortBreakSeconds));
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
        s.roomHeatCapacity = calibrated(json, "thermal_capacity_j_per_block_k", "room_heat_capacity", 1000, s.roomHeatCapacity);
        s.roomSourcePower = calibrated(json, "thermal_source_w_per_degree", "room_source_power", 250f / 1.5f, s.roomSourcePower);
        s.roomWallConductance = calibrated(json, "thermal_wall_w_per_face_k", "room_wall_conductance", 20, s.roomWallConductance);
        s.roomInsulatedConductance = calibrated(json, "thermal_insulation_w_per_face_k", "room_insulated_conductance", 20, s.roomInsulatedConductance);
        s.roomOpeningConductance = calibrated(json, "thermal_opening_w_per_face_k", "room_opening_conductance", 30, s.roomOpeningConductance);
        s.thermalTimeScale = positive(json, "thermal_time_scale", s.thermalTimeScale);
        s.airChangesPerHour = positive(json, "thermal_air_changes_per_hour", s.airChangesPerHour);
        s.cookingRoomHeatFraction = Math.min(1f, positive(json, "thermal_cooking_room_heat_fraction", s.cookingRoomHeatFraction));
        if (json.has("thermal_material_capacity_j_per_block_k") && json.get("thermal_material_capacity_j_per_block_k").isJsonObject()) {
            JsonObject capacities = json.getAsJsonObject("thermal_material_capacity_j_per_block_k");
            for (var material : ThermalConductance.Material.values())
                s.materialCapacity.put(material, positive(capacities, material.name().toLowerCase(java.util.Locale.ROOT), s.materialHeatCapacity(material)));
        }
        if (json.has("thermal_appliances")) {
            for (var entry : json.getAsJsonObject("thermal_appliances").entrySet())
                s.appliances.put(entry.getKey(), ThermalAppliance.parse(entry.getValue().getAsJsonObject()));
        }
        return s;
    }

    private static float calibrated(JsonObject json, String key, String legacy, float scale, float fallback) {
        return json.has(key) ? positive(json, key, fallback)
                : json.has(legacy) ? positive(json, legacy, fallback / scale) * scale : fallback;
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
