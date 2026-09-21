package com.aetherianartificer.townstead.decoration;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.temperature.ThermalBlocks;
import com.aetherianartificer.townstead.temperature.ThermalStructures;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of set definitions ({@code data/<ns>/decoration/*.json}) and the queries other systems ask of recognised sets. */
public final class Decorations {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/Decorations");
    private static volatile Map<ResourceLocation, DecorationDefinition> DEFINITIONS = Map.of();
    private static volatile int MAX_RADIUS = 0;
    private static volatile int MAX_THERMAL_RADIUS = 0;

    private Decorations() {}

    public static @Nullable DecorationDefinition definition(ResourceLocation id) {
        return DEFINITIONS.get(id);
    }

    public static Iterable<DecorationDefinition> all() {
        return DEFINITIONS.values();
    }

    public static boolean isEmpty() {
        return DEFINITIONS.isEmpty();
    }

    /** Use the same village boundary for catalog counts and Community Spirit contributions. */
    public static Map<ResourceLocation, Integer> countsForVillage(ServerLevel level, net.conczin.mca.server.world.data.Village village) {
        if (level == null || village == null) return Map.of();
        var centerVector = village.getCenter();
        var center = new BlockPos(centerVector.getX(), centerVector.getY(), centerVector.getZ());
        var box = village.getBox();
        int radius = Math.max(Math.max(center.getX() - box.minX(), box.maxX() - center.getX()),
                Math.max(center.getZ() - box.minZ(), box.maxZ() - center.getZ())) + 24;
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        for (var instance : DecorationSavedData.get(level).within(center, Math.min(320, radius * 2))) {
            if (instance.anchor().getX() < box.minX() - 24 || instance.anchor().getX() > box.maxX() + 24
                    || instance.anchor().getZ() < box.minZ() - 24 || instance.anchor().getZ() > box.maxZ() + 24) continue;
            if (definition(instance.decorationId()) != null) counts.merge(instance.decorationId(), 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    /** The largest recognition radius among definitions: how far a placed or broken block can matter. */
    public static int maxRadius() {
        return MAX_RADIUS;
    }

    /** Definitions the block could belong to, as anchor or member. */
    public static List<DecorationDefinition> touching(BlockState state) {
        List<DecorationDefinition> out = new ArrayList<>();
        for (DecorationDefinition definition : DEFINITIONS.values()) {
            if (definition.touches(state)) out.add(definition);
        }
        return out;
    }

    /** Summed thermal zone of every recognised warming or cooling set whose radius reaches the position. */
    public static float thermalZone(ServerLevel level, BlockPos pos) {
        if (MAX_THERMAL_RADIUS <= 0) return 0f;
        float total = 0f;
        for (DecorationInstance instance : DecorationSavedData.get(level).within(pos, MAX_THERMAL_RADIUS)) {
            ThermalStructures.Spec spec = thermalOf(level, instance);
            if (spec == null) continue;
            double dist = Math.sqrt(instance.anchor().distSqr(pos));
            if (dist > spec.radius()) continue;
            total += spec.offset() * (float) (1.0 - dist / (spec.radius() + 1.0));
        }
        return total;
    }

    /** Full offsets of thermal sets whose anchor stands inside the room. */
    public static float thermalInsideRoom(ServerLevel level, Building room) {
        if (MAX_THERMAL_RADIUS <= 0) return 0f;
        BlockPos p0 = room.getPos0();
        BlockPos p1 = room.getPos1();
        int reach = Math.max(Math.abs(p1.getX() - p0.getX()), Math.abs(p1.getZ() - p0.getZ())) / 2 + 2;
        float total = 0f;
        for (DecorationInstance instance : DecorationSavedData.get(level).within(room.getCenter(), reach)) {
            if (!room.containsPos(instance.anchor())) continue;
            ThermalStructures.Spec spec = thermalOf(level, instance);
            if (spec != null) total += spec.offset();
        }
        return total;
    }

    /** Anchors of live thermal sets of the wanted kind within the radius, nearest first. */
    public static List<BlockPos> nearestThermal(ServerLevel level, BlockPos from, int radius, boolean wantWarm, int limit) {
        List<BlockPos> out = new ArrayList<>();
        if (MAX_THERMAL_RADIUS <= 0) return out;
        for (DecorationInstance instance : DecorationSavedData.get(level).within(from, radius)) {
            ThermalStructures.Spec spec = thermalOf(level, instance);
            if (spec == null || !spec.matches(wantWarm)) continue;
            out.add(instance.anchor());
        }
        out.sort(Comparator.comparingDouble(pos -> pos.distSqr(from)));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /** The set's thermal spec when the set is live: a hearth whose fire is out gives nothing. */
    private static @Nullable ThermalStructures.Spec thermalOf(ServerLevel level, DecorationInstance instance) {
        DecorationDefinition definition = DEFINITIONS.get(instance.decorationId());
        if (definition == null || definition.thermal() == null) return null;
        if (!level.isLoaded(instance.anchor())) return null;
        BlockState anchor = level.getBlockState(instance.anchor());
        if (!definition.isAnchor(anchor)) return null;
        if (definition.thermal().warming() && !ThermalBlocks.isHeatSource(level, instance.anchor(), anchor)) return null;
        return definition.thermal();
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {
        @Override
        protected Map<ResourceLocation, JsonObject> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> entry : manager
                    .listResources("decoration", id -> id.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = entry.getKey();
                String path = file.getPath();
                ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":"
                        + path.substring("decoration/".length(), path.length() - ".json".length()));
                if (id == null) continue;
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) out.put(id, parsed.getAsJsonObject());
                } catch (Exception exception) {
                    LOGGER.warn("Failed to read decoration {}: {}", entry.getKey(), exception.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonObject> prepared, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, DecorationDefinition> loaded = new LinkedHashMap<>();
            int maxRadius = 0;
            int maxThermal = 0;
            for (Map.Entry<ResourceLocation, JsonObject> entry : prepared.entrySet()) {
                JsonObject json = entry.getValue();
                try {
                    TownsteadSchema.validate(json, DecorationDefinition.SCHEMA);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Decoration {} rejected: {}", entry.getKey(), exception.getMessage());
                    continue;
                }
                if (json.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(json.get("mods")))) continue;
                DecorationDefinition definition = DecorationDefinition.parse(entry.getKey(), json);
                if (definition == null) {
                    LOGGER.warn("Decoration {} rejected: malformed anchor or requires", entry.getKey());
                    continue;
                }
                loaded.put(entry.getKey(), definition);
                maxRadius = Math.max(maxRadius, definition.radius());
                if (definition.thermal() != null) maxThermal = Math.max(maxThermal, definition.thermal().radius());
            }
            DEFINITIONS = Map.copyOf(loaded);
            com.aetherianartificer.townstead.spirit.VillageSpiritCache.clear();
            MAX_RADIUS = maxRadius;
            MAX_THERMAL_RADIUS = maxThermal;
            LOGGER.info("Loaded {} decoration definitions", loaded.size());
        }
    }
}
