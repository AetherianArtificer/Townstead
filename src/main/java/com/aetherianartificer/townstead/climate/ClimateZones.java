package com.aetherianartificer.townstead.climate;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Climate data from {@code data/<ns>/climate/}: zone files under {@code zone/}, dimension files
 * under {@code dimension/}, and {@code derivation.json} for biomes no tag places. Each biome is resolved once
 * per reload.
 */
public final class ClimateZones {
    private static volatile Map<ResourceLocation, ClimateZone> zones = Map.of();
    private static volatile ZoneDerivation derivation = ZoneDerivation.EMPTY;
    private static final Map<ResourceLocation, ZoneDerivation.Resolution> RESOLVED = new ConcurrentHashMap<>();
    private static volatile Map<ResourceLocation, BiomeProfile> profiles = Map.of();
    private static final Map<ResourceLocation, Optional<ClimateZone>> CLIMATES = new ConcurrentHashMap<>();

    private ClimateZones() {}

    public static Map<ResourceLocation, ClimateZone> all() { return zones; }

    public static Optional<ClimateZone> get(@Nullable ResourceLocation id) {
        return id == null ? Optional.empty() : Optional.ofNullable(zones.get(id));
    }

    /**
     * The biome's climate shape: its profile when it has one (keeping its zone's id, so zone tests
     * still match), else its zone. Empty when it has neither; its dimension then decides.
     */
    public static Optional<ClimateZone> of(Holder<Biome> biome) {
        ResourceLocation key = biome.unwrapKey().map(k -> k.location()).orElse(null);
        if (key == null) return get(resolve(biome).zone());
        return CLIMATES.computeIfAbsent(key, k -> {
            ClimateZone zone = get(resolve(biome).zone()).orElse(null);
            BiomeProfile profile = profiles.get(k);
            return profile == null ? Optional.ofNullable(zone)
                    : Optional.of(profile.asZone(zone == null ? k : zone.id(), zone));
        });
    }

    /** The biome's real-climate profile, if a pack gives it one. */
    public static Optional<BiomeProfile> profile(Holder<Biome> biome) {
        return biome.unwrapKey().map(k -> profiles.get(k.location()));
    }

    /** Height above which altitude cools the biome: its profile's usual surface height, else sea level. */
    public static int referenceY(Holder<Biome> biome, int seaLevel) {
        return profile(biome).map(p -> p.typicalY() == Integer.MIN_VALUE ? seaLevel : Math.max(seaLevel, p.typicalY())).orElse(seaLevel);
    }

    /**
     * Degrees added for height: colder above the reference height, and for profiled biomes warmer
     * below it at the same rate, down to sea level.
     */
    public static float altitudeOffset(Holder<Biome> biome, int y, int seaLevel, float blocksPerDegree) {
        return altitudeOffset(y, referenceY(biome, seaLevel), seaLevel, profile(biome).isPresent(), blocksPerDegree);
    }

    static float altitudeOffset(int y, int reference, int seaLevel, boolean profiled, float blocksPerDegree) {
        if (y > reference) return -(y - reference) / blocksPerDegree;
        if (!profiled) return 0f;
        return (reference - Math.max(y, seaLevel)) / blocksPerDegree;
    }

    public static ZoneDerivation.Resolution resolve(Holder<Biome> biome) {
        ResourceLocation key = biome.unwrapKey().map(k -> k.location()).orElse(null);
        if (key == null) return derivation.resolve(facts(biome, null), zones.keySet());
        return RESOLVED.computeIfAbsent(key, k -> derivation.resolve(facts(biome, k), zones.keySet()));
    }

    /** Clears per-biome answers, for when biome tags change. */
    public static void clearResolved() { RESOLVED.clear(); CLIMATES.clear(); }

    private static ZoneDerivation.BiomeFacts facts(Holder<Biome> holder, @Nullable ResourceLocation id) {
        Biome biome = holder.value();
        return new ZoneDerivation.BiomeFacts(
                id == null ? ResourceLocation.tryParse("townstead:unregistered") : id,
                holder.tags().map(TagKey::location).collect(Collectors.toUnmodifiableSet()),
                biome.getBaseTemperature(), biome.getModifiedClimateSettings().downfall());
    }

    /** Loads zones, biome profiles ({@code climate/biome/<path>.json}, namespaced like the biome), dimensions and derivation rules. */
    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() {
            super(new Gson(), "climate");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, ClimateZone> loaded = new TreeMap<>();
            Map<ResourceLocation, DimensionClimate> dimensions = new TreeMap<>();
            Map<ResourceLocation, BiomeProfile> loadedProfiles = new TreeMap<>();
            ZoneDerivation base = ZoneDerivation.EMPTY;
            Map<String, JsonObject> packs = new TreeMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> file : new TreeMap<>(files).entrySet()) {
                ResourceLocation key = file.getKey();
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(file.getValue(), "climate");
                    if (key.getPath().startsWith("zone/")) {
                        ResourceLocation id = ResourceLocation.tryParse(key.getNamespace() + ":" + key.getPath().substring(5));
                        loaded.put(id, ClimateZone.parse(id, json));
                    } else if (key.getPath().startsWith("biome/")) {
                        ResourceLocation id = ResourceLocation.tryParse(key.getNamespace() + ":" + key.getPath().substring(6));
                        loadedProfiles.put(id, BiomeProfile.parse(json));
                    } else if (key.getPath().startsWith("dimension/")) {
                        ResourceLocation id = ResourceLocation.tryParse(key.getNamespace() + ":" + key.getPath().substring(10));
                        dimensions.put(id, DimensionClimate.parse(json));
                    } else if (key.getPath().equals("derivation")) {
                        if (key.getNamespace().equals(Townstead.MOD_ID)) base = ZoneDerivation.parse(json);
                        else packs.put(key.getNamespace(), json);
                    }
                } catch (RuntimeException error) {
                    Townstead.LOGGER.warn("[Climate] Skipping {}: {}", key, error.getMessage());
                }
            }
            for (Map.Entry<String, JsonObject> pack : packs.entrySet()) {
                try {
                    base = base.layeredUnder(ZoneDerivation.parse(pack.getValue()),
                            GsonHelper.getAsBoolean(pack.getValue(), "replace", false));
                } catch (RuntimeException error) {
                    Townstead.LOGGER.warn("[Climate] Skipping {}:derivation: {}", pack.getKey(), error.getMessage());
                }
            }
            zones = Map.copyOf(loaded);
            profiles = Map.copyOf(loadedProfiles);
            CLIMATES.clear();
            DimensionClimate.replaceAll(dimensions);
            derivation = base;
            RESOLVED.clear();
            Townstead.LOGGER.info("[Climate] Loaded {} climate zones, {} biome profiles, {} dimension climates and {} derivation rules",
                    loaded.size(), loadedProfiles.size(), dimensions.size(), base.rules().size());
        }
    }
}
