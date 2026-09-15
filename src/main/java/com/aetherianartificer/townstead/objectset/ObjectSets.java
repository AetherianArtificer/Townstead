package com.aetherianartificer.townstead.objectset;

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

/** Registry of set definitions ({@code data/<ns>/object_set/*.json}) and the queries other systems ask of recognised sets. */
public final class ObjectSets {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/ObjectSets");
    private static volatile Map<ResourceLocation, ObjectSetDefinition> DEFINITIONS = Map.of();
    private static volatile int MAX_RADIUS = 0;
    private static volatile int MAX_THERMAL_RADIUS = 0;

    private ObjectSets() {}

    public static @Nullable ObjectSetDefinition definition(ResourceLocation id) {
        return DEFINITIONS.get(id);
    }

    public static Iterable<ObjectSetDefinition> all() {
        return DEFINITIONS.values();
    }

    public static boolean isEmpty() {
        return DEFINITIONS.isEmpty();
    }

    /** The largest recognition radius among definitions: how far a placed or broken block can matter. */
    public static int maxRadius() {
        return MAX_RADIUS;
    }

    /** Definitions the block could belong to, as anchor or member. */
    public static List<ObjectSetDefinition> touching(BlockState state) {
        List<ObjectSetDefinition> out = new ArrayList<>();
        for (ObjectSetDefinition definition : DEFINITIONS.values()) {
            if (definition.touches(state)) out.add(definition);
        }
        return out;
    }

    /** Summed thermal zone of every recognised warming or cooling set whose radius reaches the position. */
    public static float thermalZone(ServerLevel level, BlockPos pos) {
        if (MAX_THERMAL_RADIUS <= 0) return 0f;
        float total = 0f;
        for (ObjectSetInstance instance : ObjectSetSavedData.get(level).within(pos, MAX_THERMAL_RADIUS)) {
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
        for (ObjectSetInstance instance : ObjectSetSavedData.get(level).within(room.getCenter(), reach)) {
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
        for (ObjectSetInstance instance : ObjectSetSavedData.get(level).within(from, radius)) {
            ThermalStructures.Spec spec = thermalOf(level, instance);
            if (spec == null || !spec.matches(wantWarm)) continue;
            out.add(instance.anchor());
        }
        out.sort(Comparator.comparingDouble(pos -> pos.distSqr(from)));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /** The set's thermal spec when the set is live: a hearth whose fire is out gives nothing. */
    private static @Nullable ThermalStructures.Spec thermalOf(ServerLevel level, ObjectSetInstance instance) {
        ObjectSetDefinition definition = DEFINITIONS.get(instance.setId());
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
                    .listResources("object_set", id -> id.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = entry.getKey();
                String path = file.getPath();
                ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":"
                        + path.substring("object_set/".length(), path.length() - ".json".length()));
                if (id == null) continue;
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) out.put(id, parsed.getAsJsonObject());
                } catch (Exception exception) {
                    LOGGER.warn("Failed to read object set {}: {}", file, exception.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonObject> prepared, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, ObjectSetDefinition> loaded = new LinkedHashMap<>();
            int maxRadius = 0;
            int maxThermal = 0;
            for (Map.Entry<ResourceLocation, JsonObject> entry : prepared.entrySet()) {
                JsonObject json = entry.getValue();
                try {
                    TownsteadSchema.validate(json, ObjectSetDefinition.SCHEMA);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Object set {} rejected: {}", entry.getKey(), exception.getMessage());
                    continue;
                }
                if (json.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(json.get("mods")))) continue;
                ObjectSetDefinition definition = ObjectSetDefinition.parse(entry.getKey(), json);
                if (definition == null) {
                    LOGGER.warn("Object set {} rejected: malformed anchor or requires", entry.getKey());
                    continue;
                }
                loaded.put(entry.getKey(), definition);
                maxRadius = Math.max(maxRadius, definition.radius());
                if (definition.thermal() != null) maxThermal = Math.max(maxThermal, definition.thermal().radius());
            }
            DEFINITIONS = Map.copyOf(loaded);
            MAX_RADIUS = maxRadius;
            MAX_THERMAL_RADIUS = maxThermal;
            LOGGER.info("Loaded {} object set definitions", loaded.size());
        }
    }
}
