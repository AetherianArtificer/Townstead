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
import java.util.Set;

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
    private static volatile List<WorkstationV2Def> V2_DEFS = List.of();

    private Workstations() {}

    public static void replaceAll(List<WorkstationDef> defs) {
        DEFS = List.copyOf(defs);
        // Defs reload alongside the recipe manager, so every cached recipe expansion is stale.
        ProtocolRecipes.invalidate();
    }

    public static List<WorkstationDef> all() {
        return DEFS;
    }

    public static List<WorkstationV2Def> v2All() {
        return V2_DEFS;
    }

    public static @Nullable WorkstationV2Def v2ByState(BlockState state) {
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (WorkstationV2Def def : V2_DEFS) if (def.blocks().contains(block)) return def;
        return null;
    }

    public static @Nullable WorkstationV2Def v2ByBlockId(@Nullable ResourceLocation block) {
        if (block == null) return null;
        for (WorkstationV2Def def : V2_DEFS) if (def.blocks().contains(block)) return def;
        return null;
    }

    /** Rebuilds the V1 task-state compatibility views after either V2 defs or attachments reload. */
    static void refreshV2CompatibilityViews() {
        List<WorkstationDef> retained = new ArrayList<>();
        for (WorkstationDef def : DEFS) {
            boolean shadowed = false;
            for (ResourceLocation block : def.blocks()) {
                if (v2ByBlockId(block) != null) { shadowed = true; break; }
            }
            if (!shadowed) retained.add(def);
        }
        for (WorkstationV2Def v2 : V2_DEFS) {
            Set<ResourceLocation> types = new java.util.LinkedHashSet<>();
            for (ResourceLocation block : v2.blocks()) types.addAll(WorkstationRecipeTypes.forBlock(block));
            retained.add(v2.legacyView(Set.copyOf(types)));
        }
        replaceAll(retained);
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

    /** The compatibility view with this stable definition id, if one is loaded. */
    public static @Nullable WorkstationDef byId(@Nullable ResourceLocation id) {
        if (id == null) return null;
        for (WorkstationDef def : DEFS) if (id.equals(def.id())) return def;
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
            List<WorkstationV2Def> v2Defs = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonObject> e : prepared.entrySet()) {
                JsonObject obj = e.getValue();
                String declaredSchema = obj.has("schema") && obj.get("schema").isJsonPrimitive()
                        ? obj.get("schema").getAsString() : SCHEMA;
                try {
                    com.aetherianartificer.townstead.data.TownsteadSchema.validate(
                            obj, WorkstationV2Def.SCHEMA.equals(declaredSchema) ? WorkstationV2Def.SCHEMA : SCHEMA);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Workstation {} rejected: {}", e.getKey(), ex.getMessage());
                    continue;
                }
                if (obj.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(obj.get("mods")))) {
                    LOGGER.debug("Workstation {} skipped: mods gate unmet or malformed", e.getKey());
                    continue;
                }
                if (WorkstationV2Def.SCHEMA.equals(declaredSchema)) {
                    WorkstationV2Def def = WorkstationV2Def.parse(e.getKey(), obj);
                    if (def == null) {
                        LOGGER.warn("Invalid V2 workstation def {}", e.getKey());
                        continue;
                    }
                    v2Defs.add(def);
                    continue;
                }
                WorkstationDef def = WorkstationDef.parse(e.getKey(), obj);
                if (def == null) {
                    LOGGER.warn("Invalid workstation def {} (needs a valid type and at least one block)", e.getKey());
                    continue;
                }
                defs.add(def);
            }
            V2_DEFS = List.copyOf(v2Defs);
            replaceAll(defs);
            refreshV2CompatibilityViews();
            if (!defs.isEmpty() || !v2Defs.isEmpty()) {
                LOGGER.info("Loaded {} V1 and {} V2 workstation defs", defs.size(), v2Defs.size());
            }
        }
    }
}
