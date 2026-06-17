package com.aetherianartificer.townstead.client.catalog;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.BuildingIconResolver;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.enclosure.EnclosureTypeIndex;
import com.aetherianartificer.townstead.origin.building.BuildingSpawnPolicies;
import com.aetherianartificer.townstead.origin.building.BuildingSpawnPolicy;
import com.aetherianartificer.townstead.spirit.BuildingSpiritIndex;
import com.aetherianartificer.townstead.spirit.SpiritRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CatalogDataLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/CatalogDataLoader");
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "townstead/catalog";
    private static final ResourceLocation CLIENT_THEME =
            ResourceLocation.tryParse(Townstead.MOD_ID + ":" + DIRECTORY + "/theme.json");

    public record GroupDef(String id, String label, String matchPrefix, String layout, String tierPrefix, int priority) {
    }

    public record BuildingOverride(Optional<ResourceLocation> nodeItem, boolean hide) {
        public static final BuildingOverride EMPTY = new BuildingOverride(Optional.empty(), false);
    }

    public record Theme(Optional<ResourceLocation> backgroundTexture, int frameColor, int panelColor,
            int titleBarColor, int graphBackgroundColor, int detailsBackgroundColor, int borderColor, int gridColor,
            boolean showGrid, int nodeFillColor, int nodeHoverFillColor, int nodeSelectedFillColor,
            int nodeBorderColor, int nodeHoverBorderColor, int nodeSelectedBorderColor, int builtNodeFillColor,
            int builtNodeHoverFillColor, int builtNodeSelectedFillColor, int builtNodeBorderColor,
            int builtNodeHoverBorderColor, int builtNodeSelectedBorderColor) {
        public static final Theme DEFAULT = new Theme(Optional.empty(), 0xFFDEDEDE, 0xFF2B2F38,
                0xFF3A3F47, 0xFF1B1E24, 0xFF232A36, 0xFF8CA2BF, 0x182A2F38, true,
                0xFF2A3342, 0xFF34435A, 0xFF3A4D66, 0xFF6D7A8D, 0xFFB8C7DB, 0xFFD9E9FF,
                0xFF1F4029, 0xFF295236, 0xFF2F5C3A, 0xFF5F9466, 0xFFA8D9AE, 0xFFCDEBD0);
    }

    private static final List<GroupDef> GROUPS = new CopyOnWriteArrayList<>();
    private static final Map<String, BuildingOverride> OVERRIDES = new LinkedHashMap<>();
    /**
     * Per-buildingType cache of {@link #matchGroup} results. Cleared whenever
     * {@code GROUPS} is repopulated (data-pack reload). Negative results are
     * cached as {@link Optional#empty()}.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Optional<GroupDef>> MATCH_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile Theme THEME = Theme.DEFAULT;
    private static volatile Theme DATA_THEME = Theme.DEFAULT;
    private static volatile ResourceManager CLIENT_THEME_RESOURCE_MANAGER = null;

    public CatalogDataLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        GROUPS.clear();
        MATCH_CACHE.clear();
        synchronized (OVERRIDES) {
            OVERRIDES.clear();
        }
        THEME = Theme.DEFAULT;
        BuildingSpiritIndex.clear();
        EnclosureTypeIndex.clear();
        BuildingIconResolver.invalidate();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation location = entry.getKey();
            String path = location.getPath();
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "catalog entry");
                if (path.startsWith("groups/")) {
                    TownsteadSchema.validate(json, "townstead:catalog_group/v1");
                    String id = location.getNamespace() + ":" + path.substring("groups/".length());
                    loadGroup(id, json);
                } else if (path.startsWith("buildings/")) {
                    TownsteadSchema.validate(json, "townstead:catalog_building/v1");
                    String buildingType = path.substring("buildings/".length());
                    loadOverride(buildingType, json);
                } else if ("theme".equals(path) && Townstead.MOD_ID.equals(location.getNamespace())) {
                    TownsteadSchema.validate(json, "townstead:catalog_theme/v1");
                    loadTheme(json);
                }
            } catch (Exception ex) {
                LOGGER.warn("Rejected catalog entry '{}': {}", location, ex.getMessage());
            }
        }

        // Legacy sources first, then the canonical extended_buildings last so it wins on conflict.
        // blocks/priority of every building_type are cached so an extended_buildings enclosure block
        // (which derives perimeter/interior from the MCA blocks map) can resolve them cross-file.
        Map<String, Map<String, Integer>> blocksByType = new HashMap<>();
        Map<String, Integer> priorityByType = new HashMap<>();
        Map<String, BuildingSpawnPolicy> spawnPolicies = new HashMap<>();
        scanLegacyBuildingTypes(resourceManager, blocksByType, priorityByType);
        scanSpiritCompanions(resourceManager);
        scanLegacyBuildingSpawn(resourceManager, spawnPolicies);
        scanExtendedBuildings(resourceManager, blocksByType, priorityByType, spawnPolicies);
        BuildingSpawnPolicies.replaceAll(spawnPolicies);
        DATA_THEME = THEME;
        CLIENT_THEME_RESOURCE_MANAGER = null;

        GROUPS.sort(Comparator.comparingInt(GroupDef::priority).reversed()
                .thenComparing(g -> -g.matchPrefix().length()));

        if (LOGGER.isInfoEnabled()) {
            StringBuilder groupList = new StringBuilder();
            for (GroupDef g : GROUPS) {
                if (groupList.length() > 0) groupList.append(", ");
                groupList.append(g.id()).append("[label='").append(g.label())
                        .append("',prefix='").append(g.matchPrefix())
                        .append("',layout=").append(g.layout()).append("]");
            }
            LOGGER.info("Catalog reload: groups={} ({}), building overrides={}, building-spirits={}",
                    GROUPS.size(), groupList, OVERRIDES.size(), BuildingSpiritIndex.size());
        }
    }

    private static void loadGroup(String id, JsonObject json) {
        String label = GsonHelper.getAsString(json, "label");
        String matchPrefix = GsonHelper.getAsString(json, "match_prefix", "");
        String layout = GsonHelper.getAsString(json, "layout", "grid");
        String tierPrefix = GsonHelper.getAsString(json, "tier_prefix", matchPrefix);
        int priority = GsonHelper.getAsInt(json, "priority", 0);
        GROUPS.add(new GroupDef(id, label, matchPrefix, layout, tierPrefix, priority));
    }

    private static void loadOverride(String buildingType, JsonObject json) {
        Optional<ResourceLocation> nodeItem = Optional.empty();
        if (json.has("node_item")) {
            ResourceLocation parsed = ResourceLocation.tryParse(GsonHelper.getAsString(json, "node_item"));
            if (parsed != null)
                nodeItem = Optional.of(parsed);
        }
        boolean hide = GsonHelper.getAsBoolean(json, "hide", false);
        putOverride(buildingType, new BuildingOverride(nodeItem, hide), true);
        if (json.has("townsteadSpirit")) {
            Map<String, Integer> spirit = parseSpiritMap(json.getAsJsonObject("townsteadSpirit"), null);
            if (!spirit.isEmpty()) BuildingSpiritIndex.put(buildingType, spirit);
        }
    }

    private static void loadTheme(JsonObject json) {
        Theme base = THEME;
        Optional<ResourceLocation> backgroundTexture = base.backgroundTexture();
        if (json.has("background_texture")) {
            ResourceLocation parsed = ResourceLocation.tryParse(GsonHelper.getAsString(json, "background_texture"));
            if (parsed != null)
                backgroundTexture = Optional.of(parsed);
        }
        THEME = new Theme(backgroundTexture,
                color(json, "frame_color", base.frameColor()),
                color(json, "panel_color", base.panelColor()),
                color(json, "title_bar_color", base.titleBarColor()),
                color(json, "graph_background_color", base.graphBackgroundColor()),
                color(json, "details_background_color", base.detailsBackgroundColor()),
                color(json, "border_color", base.borderColor()),
                color(json, "grid_color", base.gridColor()),
                GsonHelper.getAsBoolean(json, "show_grid", base.showGrid()),
                color(json, "node_fill_color", base.nodeFillColor()),
                color(json, "node_hover_fill_color", base.nodeHoverFillColor()),
                color(json, "node_selected_fill_color", base.nodeSelectedFillColor()),
                color(json, "node_border_color", base.nodeBorderColor()),
                color(json, "node_hover_border_color", base.nodeHoverBorderColor()),
                color(json, "node_selected_border_color", base.nodeSelectedBorderColor()),
                color(json, "built_node_fill_color", base.builtNodeFillColor()),
                color(json, "built_node_hover_fill_color", base.builtNodeHoverFillColor()),
                color(json, "built_node_selected_fill_color", base.builtNodeSelectedFillColor()),
                color(json, "built_node_border_color", base.builtNodeBorderColor()),
                color(json, "built_node_hover_border_color", base.builtNodeHoverBorderColor()),
                color(json, "built_node_selected_border_color", base.builtNodeSelectedBorderColor()));
    }

    private static int color(JsonObject json, String key, int fallback) {
        if (!json.has(key))
            return fallback;
        String raw = GsonHelper.getAsString(json, key).trim();
        if (raw.startsWith("#"))
            raw = raw.substring(1);
        try {
            long parsed = Long.parseLong(raw, 16);
            if (raw.length() <= 6)
                parsed |= 0xFF000000L;
            return (int) parsed;
        } catch (NumberFormatException ex) {
            LOGGER.warn("Invalid catalog theme color '{}': '{}'", key, raw);
            return fallback;
        }
    }

    private static void putOverride(String buildingType, BuildingOverride incoming, boolean preferIncoming) {
        synchronized (OVERRIDES) {
            BuildingOverride existing = OVERRIDES.get(buildingType);
            if (existing == null) {
                OVERRIDES.put(buildingType, incoming);
                return;
            }
            Optional<ResourceLocation> mergedItem = preferIncoming
                    ? incoming.nodeItem().or(existing::nodeItem)
                    : existing.nodeItem().or(incoming::nodeItem);
            boolean mergedHide = preferIncoming ? incoming.hide() || existing.hide()
                    : existing.hide() || incoming.hide();
            OVERRIDES.put(buildingType, new BuildingOverride(mergedItem, mergedHide));
        }
    }

    private static void scanLegacyBuildingTypes(ResourceManager resourceManager,
            Map<String, Map<String, Integer>> blocksByType, Map<String, Integer> priorityByType) {
        Map<ResourceLocation, Resource> resources = resourceManager.listResources("building_types",
                id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            String path = location.getPath();
            if (!path.startsWith("building_types/") || !path.endsWith(".json"))
                continue;
            String buildingType = path.substring("building_types/".length(), path.length() - ".json".length());
            try (InputStream in = entry.getValue().open();
                    InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json == null) continue;
                // Cache every type's blocks + priority so an extended_buildings enclosure block can
                // derive its perimeter/interior from the MCA building definition without re-reading.
                blocksByType.put(buildingType, readBlocks(json, location));
                priorityByType.put(buildingType, GsonHelper.getAsInt(json, "priority", 0));
                // Legacy inline townstead* fields (deprecated; extended_buildings is canonical and
                // overrides these). Kept so MCA building_types from older packs still feed our systems.
                if (json.has("townsteadNodeItem")) {
                    ResourceLocation parsed = ResourceLocation.tryParse(
                            GsonHelper.getAsString(json, "townsteadNodeItem"));
                    if (parsed != null) {
                        putOverride(buildingType, new BuildingOverride(Optional.of(parsed), false), false);
                    }
                }
                if (json.has("townsteadSpirit")) {
                    Map<String, Integer> spirit = parseSpiritMap(json.getAsJsonObject("townsteadSpirit"), location);
                    if (!spirit.isEmpty()) BuildingSpiritIndex.put(buildingType, spirit);
                }
                if (json.has("townsteadEnclosure")) {
                    JsonElement marker = json.get("townsteadEnclosure");
                    int minInterior = 4;
                    int maxInterior = 1024;
                    if (marker != null && marker.isJsonObject()) {
                        minInterior = GsonHelper.getAsInt(marker.getAsJsonObject(), "minInterior", minInterior);
                        maxInterior = GsonHelper.getAsInt(marker.getAsJsonObject(), "maxInterior", maxInterior);
                    }
                    registerEnclosure(buildingType, readBlocks(json, location),
                            GsonHelper.getAsInt(json, "priority", 0), minInterior, maxInterior);
                }
            } catch (Exception ex) {
                LOGGER.debug("Skipped legacy building_type scan for '{}': {}", location, ex.getMessage());
            }
        }
    }

    /** Read a building's {@code blocks} requirement map ({@code blockId -> count}), or empty. */
    private static Map<String, Integer> readBlocks(JsonObject json, ResourceLocation source) {
        Map<String, Integer> blocks = new HashMap<>();
        if (json.has("blocks") && json.get("blocks").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("blocks").entrySet()) {
                try {
                    blocks.put(e.getKey(), e.getValue().getAsInt());
                } catch (Exception ex) {
                    LOGGER.warn("Invalid block count for '{}' in {}: {}", e.getKey(), source, ex.getMessage());
                }
            }
        }
        return blocks;
    }

    /**
     * Register an enclosure type with {@link EnclosureTypeIndex}. Perimeter and interior requirements
     * are derived from the building's {@code blocks} map: fences / fence-gates / walls become perimeter
     * requirements, everything else becomes interior signatures that drive classification.
     */
    private static void registerEnclosure(String buildingType, Map<String, Integer> blocks,
            int priority, int minInterior, int maxInterior) {
        EnclosureTypeIndex.Spec spec = EnclosureTypeIndex.parseSpec(
                buildingType, priority, blocks, minInterior, maxInterior);
        EnclosureTypeIndex.register(spec);
        LOGGER.info("Registered enclosure type '{}' priority={} interior={}..{} fences>={} gates>={} walls>={} signatures={}",
                buildingType, priority, minInterior, maxInterior,
                spec.fencesRequired(), spec.fenceGatesRequired(), spec.wallsRequired(),
                spec.interiorSignatures().size());
    }

    /**
     * Load spirit contributions for vanilla MCA building types via companion
     * JSONs under {@code data/<ns>/spirit/<building_type>.json}. The path is
     * namespace-rooted (not nested inside another "townstead/" prefix) so
     * companion files live at {@code data/townstead/spirit/...} in our jar
     * and any add-on mod can drop {@code data/<their-ns>/spirit/...} too.
     */
    private static void scanSpiritCompanions(ResourceManager resourceManager) {
        Map<ResourceLocation, Resource> resources = resourceManager.listResources("spirit",
                id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            String path = location.getPath();
            if (!path.startsWith("spirit/") || !path.endsWith(".json")) continue;
            String buildingType = path.substring("spirit/".length(),
                    path.length() - ".json".length());
            try (InputStream in = entry.getValue().open();
                    InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json == null || !json.has("townsteadSpirit")) continue;
                TownsteadSchema.validate(json, "townstead:building_spirit/v1");
                Map<String, Integer> spirit = parseSpiritMap(json.getAsJsonObject("townsteadSpirit"), location);
                if (!spirit.isEmpty()) BuildingSpiritIndex.put(buildingType, spirit);
            } catch (Exception ex) {
                LOGGER.debug("Skipped spirit companion scan for '{}': {}", location, ex.getMessage());
            }
        }
    }

    /**
     * Legacy {@code data/<ns>/building_spawn/<type>.json} reader (deprecated; superseded by the
     * {@code spawn} block of {@code extended_buildings}). Kept so older packs still feed spawn policy.
     */
    private static void scanLegacyBuildingSpawn(ResourceManager resourceManager,
            Map<String, BuildingSpawnPolicy> out) {
        Map<ResourceLocation, Resource> resources = resourceManager.listResources("building_spawn",
                id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            String path = entry.getKey().getPath();
            if (!path.startsWith("building_spawn/") || !path.endsWith(".json")) continue;
            String buildingType = path.substring("building_spawn/".length(), path.length() - ".json".length());
            try (InputStream in = entry.getValue().open();
                    InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null) out.put(buildingType, BuildingSpawnPolicy.parse(json));
            } catch (Exception ex) {
                LOGGER.debug("Skipped legacy building_spawn scan for '{}': {}", entry.getKey(), ex.getMessage());
            }
        }
    }

    /**
     * Canonical {@code data/<ns>/extended_buildings/<building_type>.json}: all Townstead per-building
     * data in one file, keyed by MCA building-type id, so MCA's own {@code building_types} JSON stays
     * vanilla. Blocks: {@code catalog} (node_item/hide), {@code spirit}, {@code spawn}, {@code enclosure}.
     * Runs after the legacy readers and overrides them. The {@code enclosure} block derives its
     * perimeter/interior from the building's MCA {@code blocks} map (cached in {@code blocksByType}).
     */
    private static void scanExtendedBuildings(ResourceManager resourceManager,
            Map<String, Map<String, Integer>> blocksByType, Map<String, Integer> priorityByType,
            Map<String, BuildingSpawnPolicy> spawnPolicies) {
        Map<ResourceLocation, Resource> resources = resourceManager.listResources("extended_buildings",
                id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            String path = location.getPath();
            if (!path.startsWith("extended_buildings/") || !path.endsWith(".json")) continue;
            String buildingType = path.substring("extended_buildings/".length(), path.length() - ".json".length());
            try (InputStream in = entry.getValue().open();
                    InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json == null) continue;
                TownsteadSchema.validate(json, "townstead:extended_building/v1");

                if (json.has("catalog") && json.get("catalog").isJsonObject()) {
                    JsonObject cat = json.getAsJsonObject("catalog");
                    Optional<ResourceLocation> nodeItem = Optional.empty();
                    if (cat.has("node_item")) {
                        ResourceLocation parsed = ResourceLocation.tryParse(GsonHelper.getAsString(cat, "node_item"));
                        if (parsed != null) nodeItem = Optional.of(parsed);
                    }
                    boolean hide = GsonHelper.getAsBoolean(cat, "hide", false);
                    putOverride(buildingType, new BuildingOverride(nodeItem, hide), true);
                }
                if (json.has("spirit") && json.get("spirit").isJsonObject()) {
                    Map<String, Integer> spirit = parseSpiritMap(json.getAsJsonObject("spirit"), location);
                    if (!spirit.isEmpty()) BuildingSpiritIndex.put(buildingType, spirit);
                }
                if (json.has("spawn") && json.get("spawn").isJsonObject()) {
                    spawnPolicies.put(buildingType, BuildingSpawnPolicy.parse(json.getAsJsonObject("spawn")));
                }
                if (json.has("enclosure") && json.get("enclosure").isJsonObject()) {
                    JsonObject enc = json.getAsJsonObject("enclosure");
                    Map<String, Integer> blocks = blocksByType.getOrDefault(buildingType, Map.of());
                    registerEnclosure(buildingType, blocks, priorityByType.getOrDefault(buildingType, 0),
                            GsonHelper.getAsInt(enc, "minInterior", 4), GsonHelper.getAsInt(enc, "maxInterior", 1024));
                }
            } catch (Exception ex) {
                LOGGER.warn("Rejected extended_buildings entry '{}': {}", location, ex.getMessage());
            }
        }
    }

    /**
     * Parse a {@code townsteadSpirit} JSON object into an immutable int-valued
     * map. Unknown spirit ids warn-log and are dropped. Non-positive values
     * are dropped silently (a "0" entry is a no-op).
     */
    private static Map<String, Integer> parseSpiritMap(JsonObject obj, ResourceLocation source) {
        if (obj == null || obj.size() == 0) return Map.of();
        Map<String, Integer> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            String spiritId = e.getKey();
            if (!SpiritRegistry.contains(spiritId)) {
                LOGGER.warn("Unknown spirit id '{}' in {}; ignored", spiritId, source);
                continue;
            }
            try {
                int pts = e.getValue().getAsInt();
                if (pts > 0) out.put(spiritId, pts);
            } catch (Exception ex) {
                LOGGER.warn("Invalid spirit value for '{}' in {}: {}", spiritId, source, ex.getMessage());
            }
        }
        return out;
    }

    public static List<GroupDef> groups() {
        return GROUPS;
    }

    public static BuildingOverride overrideFor(String buildingType) {
        synchronized (OVERRIDES) {
            return OVERRIDES.getOrDefault(buildingType, BuildingOverride.EMPTY);
        }
    }

    public static Theme theme() {
        return THEME;
    }

    public static void refreshClientTheme(ResourceManager resourceManager) {
        if (resourceManager == null || resourceManager == CLIENT_THEME_RESOURCE_MANAGER)
            return;
        CLIENT_THEME_RESOURCE_MANAGER = resourceManager;
        THEME = DATA_THEME;
        Optional<Resource> resource = resourceManager.getResource(CLIENT_THEME);
        if (resource.isEmpty())
            return;
        try (InputStream in = resource.get().open();
                InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null)
                loadTheme(json);
        } catch (Exception ex) {
            LOGGER.warn("Rejected client catalog theme '{}': {}", CLIENT_THEME, ex.getMessage());
        }
    }

    public static Optional<GroupDef> matchGroup(String buildingType) {
        if (buildingType == null) return Optional.empty();
        Optional<GroupDef> cached = MATCH_CACHE.get(buildingType);
        if (cached != null) return cached;
        Optional<GroupDef> resolved = Optional.empty();
        for (GroupDef g : GROUPS) {
            if (!g.matchPrefix().isEmpty() && buildingType.startsWith(g.matchPrefix())) {
                resolved = Optional.of(g);
                break;
            }
        }
        MATCH_CACHE.put(buildingType, resolved);
        return resolved;
    }
}
