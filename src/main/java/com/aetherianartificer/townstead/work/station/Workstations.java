package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.ModGate;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of data-declared workstations, replaced each datapack reload. Consulted from the
 * station-type truth table and the pot-protocol layout lookups; empty (the common case with no
 * packs installed) costs one volatile read on the scan hot path.
 */
public final class Workstations {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/Workstations");

    /**
     * Optional version marker, matching every other author-facing Townstead document. Omitting it
     * reads as "current", so existing packs keep loading; declaring the wrong one is refused
     * rather than half-read, which is the point of having it once the format grows a v2.
     */
    public static final String SCHEMA = "townstead:workstation/v1";

    private static volatile List<WorkstationDef> DEFS = List.of();

    private Workstations() {}

    public static void replaceAll(List<WorkstationDef> defs) {
        DEFS = List.copyOf(defs);
    }

    public static List<WorkstationDef> all() {
        return DEFS;
    }

    public static @Nullable WorkstationDef byState(BlockState state) {
        List<WorkstationDef> defs = DEFS;
        if (defs.isEmpty()) return null;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (WorkstationDef def : defs) {
            if (def.blocks().contains(id)) return def;
            for (ResourceLocation tag : def.blockTags()) {
                if (state.is(TagKey.create(Registries.BLOCK, tag))) return def;
            }
        }
        return null;
    }

    public static @Nullable WorkstationDef byBlockId(@Nullable ResourceLocation blockId) {
        List<WorkstationDef> defs = DEFS;
        if (blockId == null || defs.isEmpty()) return null;
        for (WorkstationDef def : defs) {
            if (def.blocks().contains(blockId)) return def;
        }
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        for (WorkstationDef def : defs) {
            for (ResourceLocation tag : def.blockTags()) {
                if (block.defaultBlockState().is(TagKey.create(Registries.BLOCK, tag))) return def;
            }
        }
        return null;
    }

    /** The station's declared recipe type, for exclusive recipe/station pairing; null when built-in. */
    public static @Nullable ResourceLocation declaredRecipeTypeAt(ServerLevel level, BlockPos pos) {
        if (DEFS.isEmpty()) return null;
        WorkstationDef def = byState(level.getBlockState(pos));
        return def == null ? null : def.recipeType();
    }

    /** Loads {@code data/<ns>/workstation/*.json}; defs behind unmet {@code mods} gates don't exist. */
    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {

        @Override
        protected Map<ResourceLocation, JsonObject> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> e : resourceManager
                    .listResources("workstation", loc -> loc.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = e.getKey();
                String path = file.getPath();
                ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":"
                        + path.substring("workstation/".length(), path.length() - ".json".length()));
                if (id == null) continue;
                try (Reader reader = e.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) out.put(id, parsed.getAsJsonObject());
                } catch (Exception ex) {
                    LOGGER.warn("Failed to read workstation {}: {}", file, ex.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonObject> prepared, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            List<WorkstationDef> defs = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonObject> e : prepared.entrySet()) {
                JsonObject obj = e.getValue();
                try {
                    com.aetherianartificer.townstead.data.TownsteadSchema.validate(obj, SCHEMA);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Workstation {} rejected: {}", e.getKey(), ex.getMessage());
                    continue;
                }
                if (obj.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(obj.get("mods")))) {
                    LOGGER.debug("Workstation {} skipped: mods gate unmet or malformed", e.getKey());
                    continue;
                }
                WorkstationDef def = WorkstationDef.parse(e.getKey(), obj);
                if (def == null) {
                    LOGGER.warn("Invalid workstation def {} (needs a valid type and at least one block)", e.getKey());
                    continue;
                }
                defs.add(def);
            }
            replaceAll(defs);
            if (!defs.isEmpty()) LOGGER.info("Loaded {} workstation defs", defs.size());
        }
    }
}
