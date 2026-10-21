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

    // Vanilla biome temperature to the yearly mean in Celsius; zones add the seasons around it.
    private float[] anchorBiome = {-0.5f, 0.15f, 0.8f, 0.95f, 2.0f};
    private float[] anchorCelsius = {-12f, -3f, 13f, 24f, 26f};
    private final Map<Season, Float> seasonOffsets = new EnumMap<>(Season.class);
    private float altitudeBlocksPerDegree = 15f;
    private float rainOffset = 0f;
    // Legacy extras on top of the climate model; zones now carry the day/night range and seasons.
    private float nightOffset = 0f;
    private float netherCelsius = 45f;
    private float endCelsius = 5f;
    private int sourceRadius = 10;
    private float heatSourceOffset = 12f;
    private float coolingSourceOffset = -8f;
    // Tough As Nails has no numeric temperature at all, only five steps from ICY to HOT, so a
    // warming in degrees has to be quantised before it can shift the player's level.
    private float celsiusPerTanStep = 8f;
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
    // Radiant strength per degree of a source's face above the air, per block face of size.
    private float radiantPerSurfaceDegree = 0.0667f;
    // A drafting source adds watts / rise W/K of outside air, so fires alone never lift a room past it.
    private float fireMaxRise = 25f;
    private float radiantMax = (float) RoomHeatBalance.RADIANT_MAX, radiantStack = (float) RoomHeatBalance.RADIANT_STACK,
            radiantColdStack = (float) RoomHeatBalance.RADIANT_COLD_STACK;
    private final Map<com.aetherianartificer.townstead.temperature.ThermalConductance.Material, Float> materialCapacity =
            new EnumMap<>(com.aetherianartificer.townstead.temperature.ThermalConductance.Material.class);
    private final Map<String, ThermalAppliance> appliances = new java.util.LinkedHashMap<>();
    public ThermalAppliance appliance(String id) { return appliances.get(id); }
    public float materialHeatCapacity(ThermalConductance.Material material) { return materialCapacity.get(material); }
    public float radiantPerSurfaceDegree() { return radiantPerSurfaceDegree; }
    public float fireMaxRise() { return fireMaxRise; }
    public float radiantMax() { return radiantMax; }
    public float radiantStack() { return radiantStack; }
    public float radiantColdStack() { return radiantColdStack; }
    public float thermalTimeScale() { return thermalTimeScale; }
    public float airChangesPerHour() { return airChangesPerHour; }
    public boolean roomHeatEnabled() { return roomHeatEnabled; }
    public float roomHeatCapacity() { return roomHeatCapacity; }
    public float roomSourcePower() { return roomSourcePower; }
    public float roomWallConductance() { return roomWallConductance; }
    public float roomInsulatedConductance() { return roomInsulatedConductance; }
    public float roomOpeningConductance() { return roomOpeningConductance; }
    // Ground: the annual season wave fades e-fold per damping depth; covered positions blend to it.
    private float groundDampingDepth = 3f, surfaceLayer = 3f;
    // Rock warms below the start height, faster in deepslate: a whole real mine compressed into the world.
    private int geothermalStartY = 48, geothermalDeepY = 0;
    private float geothermalPerBlock = 0.15f, geothermalDeepPerBlock = 0.2f;
    // Buried lava warms rock by heat / distance per block, summed and capped.
    private int lavaRockRadius = 12;
    private float lavaRockHeat = 1.5f, lavaRockMax = 25f;
    // Snow is weather: falling snow caps the air, lying snow holds the day near freezing and chills clear nights.
    private float snowfallCelsius = -3f, snowDayMax = 1f, snowNightCooling = -5f;
    // Cloud and rain shrink the day/night range to this fraction.
    private float cloudDiurnalFactor = 0.4f;
    // Falling rain cools the air this share of the way to its wet-bulb temperature, midday to night.
    private float rainWetBulbDay = 0.4f, rainWetBulbNight = 0.2f;
    // Yearly means for biomes whose vanilla temperature says little (oceans share one value), by id or #tag.
    private final Map<String, Float> biomeMeans = new java.util.LinkedHashMap<>();

    /** A pack-set yearly mean for this biome, by id first, then by tag in file order. */
    public java.util.OptionalDouble biomeMean(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        if (biomeMeans.isEmpty()) return java.util.OptionalDouble.empty();
        String id = biome.unwrapKey().map(k -> k.location().toString()).orElse(null);
        if (id != null && biomeMeans.containsKey(id)) return java.util.OptionalDouble.of(biomeMeans.get(id));
        for (Map.Entry<String, Float> entry : biomeMeans.entrySet()) {
            if (!entry.getKey().startsWith("#")) continue;
            ResourceLocation tag = ResourceLocation.tryParse(entry.getKey().substring(1));
            if (tag != null && biome.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, tag)))
                return java.util.OptionalDouble.of(entry.getValue());
        }
        return java.util.OptionalDouble.empty();
    }
    /** Air range a crop class grows in, in Celsius; open ends are infinite. */
    public record CropRange(float min, float max) {}
    // Crops tagged #townstead:crops/climate/<class> grow only inside their class's range.
    private final Map<String, CropRange> cropClasses = new java.util.LinkedHashMap<>(Map.of(
            "hardy", new CropRange(-5f, Float.POSITIVE_INFINITY),
            "cool_season", new CropRange(0f, 26f),
            "temperate", new CropRange(2f, Float.POSITIVE_INFINITY),
            "warm_season", new CropRange(8f, Float.POSITIVE_INFINITY),
            "tropical", new CropRange(18f, Float.POSITIVE_INFINITY)));
    private String defaultCropClass = "temperate";
    public Map<String, CropRange> cropClasses() { return cropClasses; }
    public String defaultCropClass() { return defaultCropClass; }
    // With no season mod, every day sits this share of the zone's swing above the yearly mean.
    private float noSeasonSwingFraction = 0.65f;
    public float noSeasonSwingFraction() { return noSeasonSwingFraction; }
    public float snowfallCelsius() { return snowfallCelsius; }
    public float snowDayMax() { return snowDayMax; }
    public float snowNightCooling() { return snowNightCooling; }
    public float cloudDiurnalFactor() { return cloudDiurnalFactor; }
    public float rainWetBulbDay() { return rainWetBulbDay; }
    public float rainWetBulbNight() { return rainWetBulbNight; }
    public float groundDampingDepth() { return groundDampingDepth; }
    public float surfaceLayer() { return surfaceLayer; }
    public int geothermalStartY() { return geothermalStartY; }
    public int geothermalDeepY() { return geothermalDeepY; }
    public float geothermalPerBlock() { return geothermalPerBlock; }
    public float geothermalDeepPerBlock() { return geothermalDeepPerBlock; }
    public int lavaRockRadius() { return lavaRockRadius; }
    public float lavaRockHeat() { return lavaRockHeat; }
    public float lavaRockMax() { return lavaRockMax; }
    private float dryingSeconds = 90;
    // Effective temperatures within this many degrees of 20 C leave the body alone.
    // Feels-like range a body is comfortable in without a temperature mod.
    private ComfortRange comfortRange = new ComfortRange(15f, 27f);
    // Acclimation without a temperature mod's own: range shifts at full adaptation, and its pace.
    private ThermalAcclimation.Limits acclimationLimits = new ThermalAcclimation.Limits(-4f, -2f, 2f, 4f, 1f);
    private float acclimationRatePerMinute = 1f / 60f;
    // Time constant of the body's drift toward what its surroundings would settle it at.
    private float bodyResponseSeconds = 480f;
    private float bodyRecoverySeconds = 120f;

    private static ThermalAppliance surface(double watts, float nativeReference, boolean draft, float surfaceCelsius) {
        return new ThermalAppliance(watts, Float.NaN, nativeReference, draft, false, surfaceCelsius, Float.NaN, 1f);
    }

    private TemperatureSettings() {
        appliances.put("legendarysurvivaloverhaul:heater", surface(3750, 15, false, 250));
        appliances.put("legendarysurvivaloverhaul:cooler", surface(-3750, -15, true, -20));
        appliances.put("legendarysurvivaloverhaul:heater_top", new ThermalAppliance(0, 0, 0));
        appliances.put("cold_sweat:hearth_bottom", new ThermalAppliance(4000, Float.NaN, 1, false, true, 300, -10, 1));
        appliances.put("cold_sweat:boiler", surface(3000, 1, false, 200));
        appliances.put("cold_sweat:icebox", surface(-3000, -1, false, -10));
        appliances.put("minecraft:campfire", surface(5000, 0, true, 500));
        appliances.put("minecraft:soul_campfire", surface(3500, 0, true, 300));
        appliances.put("minecraft:furnace", surface(2500, 0, true, 200));
        appliances.put("minecraft:smoker", surface(2500, 0, true, 200));
        appliances.put("minecraft:blast_furnace", surface(4000, 0, true, 300));
        materialCapacity.put(ThermalConductance.Material.INSULATION, 500f);
        materialCapacity.put(ThermalConductance.Material.WOOD, 4000f);
        materialCapacity.put(ThermalConductance.Material.EARTH, 16000f);
        materialCapacity.put(ThermalConductance.Material.MASONRY, 12000f);
        materialCapacity.put(ThermalConductance.Material.GLASS, 6000f);
        materialCapacity.put(ThermalConductance.Material.METAL, 16000f);
        materialCapacity.put(ThermalConductance.Material.POROUS, 1000f);
        seasonOffsets.put(Season.SPRING, 0f);
        seasonOffsets.put(Season.SUMMER, 0f);
        seasonOffsets.put(Season.AUTUMN, 0f);
        seasonOffsets.put(Season.WINTER, 0f);
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
    public float celsiusPerTanStep() { return celsiusPerTanStep; }
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
    public ComfortRange comfortRange() { return comfortRange; }
    public ThermalAcclimation.Limits acclimationLimits() { return acclimationLimits; }
    public float acclimationRatePerMinute() { return acclimationRatePerMinute; }
    public float bodyResponseSeconds() { return bodyResponseSeconds; }
    public float bodyRecoverySeconds() { return bodyRecoverySeconds; }

    static TemperatureSettings parse(JsonObject json) {
        TemperatureSettings s = new TemperatureSettings();
        s.dryingSeconds = Math.max(1, GsonHelper.getAsFloat(json, "drying_seconds", 90));
        if (json.has("comfort_range_c")) {
            var range = GsonHelper.getAsJsonArray(json, "comfort_range_c");
            float low = range.get(0).getAsFloat(), high = range.get(1).getAsFloat();
            if (high > low) s.comfortRange = new ComfortRange(low, high);
        }
        if (json.has("acclimation")) {
            ThermalAcclimation.Limits limits = ThermalAcclimation.Limits.parse(json.get("acclimation"));
            if (limits != null) s.acclimationLimits = limits.over(s.acclimationLimits);
            if (json.get("acclimation").isJsonObject() && json.getAsJsonObject("acclimation").has("full_minutes"))
                s.acclimationRatePerMinute = 1f / Math.max(1f, GsonHelper.getAsFloat(json.getAsJsonObject("acclimation"), "full_minutes"));
        }
        s.bodyResponseSeconds = positive(json, "body_response_seconds", s.bodyResponseSeconds);
        s.bodyRecoverySeconds = positive(json, "body_recovery_seconds", s.bodyRecoverySeconds);
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
        s.celsiusPerTanStep = Math.max(0.5f, GsonHelper.getAsFloat(json, "celsius_per_tan_step", s.celsiusPerTanStep));
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
        if (json.has("biome_mean_c") && json.get("biome_mean_c").isJsonObject()) {
            for (var entry : json.getAsJsonObject("biome_mean_c").entrySet())
                s.biomeMeans.put(entry.getKey(), entry.getValue().getAsFloat());
        }
        if (json.has("crop_climate") && json.get("crop_climate").isJsonObject()) {
            for (var entry : json.getAsJsonObject("crop_climate").entrySet()) {
                JsonObject range = entry.getValue().getAsJsonObject();
                s.cropClasses.put(entry.getKey(), new CropRange(
                        GsonHelper.getAsFloat(range, "min_c", Float.NEGATIVE_INFINITY),
                        GsonHelper.getAsFloat(range, "max_c", Float.POSITIVE_INFINITY)));
            }
        }
        s.defaultCropClass = GsonHelper.getAsString(json, "crop_default_class", s.defaultCropClass);
        s.noSeasonSwingFraction = Math.max(-1f, Math.min(1f, GsonHelper.getAsFloat(json, "no_season_swing_fraction", s.noSeasonSwingFraction)));
        s.snowfallCelsius = GsonHelper.getAsFloat(json, "snowfall_c", s.snowfallCelsius);
        s.snowDayMax = GsonHelper.getAsFloat(json, "snow_day_max_c", s.snowDayMax);
        s.snowNightCooling = Math.min(0f, GsonHelper.getAsFloat(json, "snow_night_cooling_c", s.snowNightCooling));
        s.cloudDiurnalFactor = Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(json, "cloud_diurnal_factor", s.cloudDiurnalFactor)));
        s.rainWetBulbDay = Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(json, "rain_wet_bulb_fraction_day", s.rainWetBulbDay)));
        s.rainWetBulbNight = Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(json, "rain_wet_bulb_fraction_night", s.rainWetBulbNight)));
        s.groundDampingDepth = positive(json, "ground_damping_depth_blocks", s.groundDampingDepth);
        s.surfaceLayer = Math.max(0f, GsonHelper.getAsFloat(json, "surface_layer_blocks", s.surfaceLayer));
        s.geothermalStartY = GsonHelper.getAsInt(json, "geothermal_start_y", s.geothermalStartY);
        s.geothermalDeepY = Math.min(s.geothermalStartY, GsonHelper.getAsInt(json, "geothermal_deepslate_y", s.geothermalDeepY));
        s.geothermalPerBlock = Math.max(0f, GsonHelper.getAsFloat(json, "geothermal_c_per_block", s.geothermalPerBlock));
        s.geothermalDeepPerBlock = Math.max(0f, GsonHelper.getAsFloat(json, "geothermal_deepslate_c_per_block", s.geothermalDeepPerBlock));
        s.lavaRockRadius = Math.max(0, Math.min(24, GsonHelper.getAsInt(json, "lava_rock_radius", s.lavaRockRadius)));
        s.lavaRockHeat = Math.max(0f, GsonHelper.getAsFloat(json, "lava_rock_c", s.lavaRockHeat));
        s.lavaRockMax = Math.max(0f, GsonHelper.getAsFloat(json, "lava_rock_max_c", s.lavaRockMax));
        s.thermalTimeScale = positive(json, "thermal_time_scale", s.thermalTimeScale);
        s.airChangesPerHour = positive(json, "thermal_air_changes_per_hour", s.airChangesPerHour);
        s.radiantPerSurfaceDegree = positive(json, "thermal_radiant_per_surface_degree", s.radiantPerSurfaceDegree);
        s.fireMaxRise = positive(json, "thermal_fire_max_rise_c", s.fireMaxRise);
        s.radiantMax = positive(json, "thermal_radiant_max_c", s.radiantMax);
        s.radiantStack = Math.max(1f, positive(json, "thermal_radiant_stack_limit", s.radiantStack));
        s.radiantColdStack = Math.max(1f, positive(json, "thermal_radiant_cold_stack_limit", s.radiantColdStack));
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
            ThermalBlocks.clearCache();
            ThermalReliefTargets.clearCache();
            GroundClimate.clearCache();
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
