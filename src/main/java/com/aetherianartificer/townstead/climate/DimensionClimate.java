package com.aetherianartificer.townstead.climate;

import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.temperature.TemperatureSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * How a dimension's climate works: biome climates or one fixed temperature, whether the season
 * clock and the day/night curve apply, whether weather and the ground model apply, and vertical
 * offsets. With biome climates, {@code default_c} is the mean of a biome with neither a profile nor a
 * zone. Dimensions without a file take defaults from their dimension type.
 */
public record DimensionClimate(Float fixedCelsius, boolean seasons, boolean dayCycle, boolean weather,
                               boolean underground, List<Layer> layers,
                               Float defaultCelsius) {
    public static final String SCHEMA = "townstead:climate_dimension/v1";

    /** An offset above or below a height; both bounds are optional. */
    public record Layer(int aboveY, int belowY, float offset) {
        boolean contains(int y) { return y >= aboveY && y < belowY; }
    }

    private static volatile Map<ResourceLocation, DimensionClimate> FILES = Map.of();

    public static DimensionClimate of(ServerLevel level) {
        DimensionClimate file = FILES.get(level.dimension().location());
        return file != null ? file : defaults(level.dimensionType(), level.dimension().location());
    }

    static void replaceAll(Map<ResourceLocation, DimensionClimate> files) {
        FILES = Map.copyOf(files);
    }

    public boolean usesBiomes() { return fixedCelsius == null; }

    public float layerOffset(int y) {
        float offset = 0f;
        for (Layer layer : layers) if (layer.contains(y)) offset += layer.offset();
        return offset;
    }

    /** Fixed heat for ultrawarm types, fixed cold for unnatural ones, roofed worlds without sky or seasons. */
    static DimensionClimate defaults(DimensionType type, ResourceLocation id) {
        TemperatureSettings s = TemperatureSettings.get();
        if (type.ultraWarm()) return new DimensionClimate(s.netherCelsius(), false, false, false, false, List.of(), null);
        if (!type.natural()) return new DimensionClimate(s.endCelsius(), false, false, false, false, List.of(), null);
        if (type.hasCeiling()) return new DimensionClimate(null, false, false, false, false, List.of(), null);
        boolean overworld = id.getNamespace().equals("minecraft") && id.getPath().equals("overworld");
        return new DimensionClimate(null, overworld, type.hasSkyLight(), type.hasSkyLight(), true, List.of(), null);
    }

    static DimensionClimate parse(JsonObject json) {
        TownsteadSchema.validate(json, SCHEMA);
        Float fixed = null;
        JsonElement temperature = json.get("temperature");
        if (temperature != null && temperature.isJsonPrimitive() && temperature.getAsJsonPrimitive().isNumber())
            fixed = temperature.getAsFloat();
        else if (temperature != null && !"biomes".equals(temperature.getAsString()))
            throw new IllegalArgumentException("temperature must be \"biomes\" or a number");
        String clock = GsonHelper.getAsString(json, "season_clock", "overworld");
        if (!clock.equals("overworld") && !clock.equals("none"))
            throw new IllegalArgumentException("season_clock must be \"overworld\" or \"none\"");
        List<Layer> layers = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "layers", new JsonArray())) {
            JsonObject layer = GsonHelper.convertToJsonObject(element, "layer");
            layers.add(new Layer(GsonHelper.getAsInt(layer, "above_y", Integer.MIN_VALUE),
                    GsonHelper.getAsInt(layer, "below_y", Integer.MAX_VALUE),
                    GsonHelper.getAsFloat(layer, "offset_c")));
        }
        return new DimensionClimate(fixed, clock.equals("overworld"),
                GsonHelper.getAsBoolean(json, "day_cycle", fixed == null),
                GsonHelper.getAsBoolean(json, "weather", fixed == null),
                GsonHelper.getAsBoolean(json, "underground", fixed == null),
                List.copyOf(layers),
                json.has("default_c") ? GsonHelper.getAsFloat(json, "default_c") : null);
    }
}
