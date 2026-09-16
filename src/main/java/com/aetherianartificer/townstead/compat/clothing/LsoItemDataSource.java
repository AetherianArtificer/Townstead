package com.aetherianartificer.townstead.compat.clothing;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.clothing.ClothingThermal;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.aetherianartificer.townstead.temperature.ThermalProtection;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Garment data that mods ship for Legendary Survival Overhaul, read straight from their jars.
 *
 * <p>Weavers' Paradise and others author warmth for LSO under
 * {@code data/<ns>/legendarysurvivaloverhaul/temperature/items/<item>.json}. When LSO is
 * installed its bridge answers first and this source is never reached. When it is not, this
 * source gives the same garments the same warmth, so a pack's choice of temperature mod does not
 * change what a wool sweater does. The fields and scale are LSO's, mapped exactly as the LSO
 * bridge maps them.</p>
 */
public final class LsoItemDataSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/LsoItemDataSource");
    private static final String DIR = "legendarysurvivaloverhaul/temperature/items";

    private static volatile Map<ResourceLocation, ThermalProtection> ITEMS = Map.of();

    static {
        ClothingThermal.registerStackSource(LsoItemDataSource::protection);
    }

    private LsoItemDataSource() {}

    /** Forces the static registration; the loader's constructor is the natural place to call it. */
    public static void bootstrap() {}

    public static void replaceAll(Map<ResourceLocation, ThermalProtection> items) {
        ITEMS = Map.copyOf(items);
        ClothingThermal.invalidate();
    }

    public static @Nullable ThermalProtection protection(ItemStack stack) {
        if (stack == null || stack.isEmpty() || ITEMS.isEmpty()) return null;
        return ITEMS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** LSO's {@code temperature} is a flat offset on its ambient scale; the resistances are direct. */
    static @Nullable ThermalProtection parse(JsonObject json) {
        if (json == null) return null;
        ThermalProtection protection = new ThermalProtection(
                GsonHelper.getAsFloat(json, "temperature", 0f) * TemperatureData.AMBIENT_PULL_PER_DEGREE,
                GsonHelper.getAsFloat(json, "cold_resistance", 0f),
                GsonHelper.getAsFloat(json, "heat_resistance", 0f),
                GsonHelper.getAsFloat(json, "thermal_resistance", 0f));
        return protection.equals(ThermalProtection.NONE) ? null : protection;
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {

        public Loader() {
            bootstrap();
        }

        @Override
        protected Map<ResourceLocation, JsonObject> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> e : resourceManager
                    .listResources(DIR, loc -> loc.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = e.getKey();
                String path = file.getPath();
                ResourceLocation item = DataPackLang.parseId(file.getNamespace() + ":"
                        + path.substring(DIR.length() + 1, path.length() - ".json".length()));
                if (item == null) continue;
                try (Reader reader = e.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) out.put(item, parsed.getAsJsonObject());
                } catch (Exception ex) {
                    LOGGER.debug("Skipped LSO item data {}: {}", file, ex.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonObject> prepared, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, ThermalProtection> items = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonObject> e : prepared.entrySet()) {
                ThermalProtection protection = parse(e.getValue());
                if (protection != null) items.put(e.getKey(), protection);
            }
            replaceAll(items);
            if (!items.isEmpty()) LOGGER.info("Read LSO garment data for {} items", items.size());
        }
    }
}
