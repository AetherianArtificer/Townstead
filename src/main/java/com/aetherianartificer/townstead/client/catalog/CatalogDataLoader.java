package com.aetherianartificer.townstead.client.catalog;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.BuildingIconResolver;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.enclosure.EnclosureTypeIndex;
import com.aetherianartificer.townstead.root.building.BuildingSpawnPolicies;
import com.aetherianartificer.townstead.root.building.BuildingSpawnPolicy;
import com.aetherianartificer.townstead.recognition.BuildingEnclosurePolicies;
import com.aetherianartificer.townstead.spirit.BuildingSpiritIndex;
import com.aetherianartificer.townstead.spirit.SpiritRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CatalogDataLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/CatalogDataLoader");
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "townstead/catalog";
    private static final ResourceLocation CLIENT_THEME =
            ResourceLocation.tryParse(Townstead.MOD_ID + ":" + DIRECTORY + "/theme.json");

    public record GroupDef(String id, String label, String matchPrefix, String layout, String tierPrefix,
                           int priority, List<String> supersedes) {
        public GroupDef {
            supersedes = supersedes == null ? List.of() : List.copyOf(supersedes);
        }
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
        BuildingIconResolver.beginBuildingTypeReload();

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
                    if (!ModCompat.isCompatAvailable(buildingType)) continue;
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
        Map<String, List<ResourceLocation>> workersByType = new HashMap<>();
        Map<String, BuildingEnclosurePolicies.Mode> enclosurePolicies = new HashMap<>();
        Map<String, Set<String>> dialogueTopicsByType = new HashMap<>();
        scanLegacyBuildingTypes(resourceManager, blocksByType, priorityByType);
        scanSpiritCompanions(resourceManager);
        scanLegacyBuildingSpawn(resourceManager, spawnPolicies);
        scanExtendedBuildings(resourceManager, blocksByType, priorityByType, spawnPolicies, workersByType,
                enclosurePolicies, dialogueTopicsByType);
        BuildingSpawnPolicies.replaceAll(spawnPolicies);
        com.aetherianartificer.townstead.work.site.BuildingWorkforceIndex.replaceAll(workersByType);
        BuildingEnclosurePolicies.replaceAll(enclosurePolicies);
        com.aetherianartificer.townstead.work.feedback.BuildingDialogueTopics
                .replaceAll(dialogueTopicsByType);
        // The icon-to-type index and node-item overrides are now both complete.
        // Clear any negative result cached while parallel reload listeners ran.
        BuildingIconResolver.invalidate();
        com.aetherianartificer.townstead.compat.mca.McaBuildingDiscovery.invalidateSignatures();
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
        List<String> supersedes = new java.util.ArrayList<>();
        if (json.has("supersedes") && json.get("supersedes").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("supersedes")) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) continue;
                String buildingType = element.getAsString().trim();
                if (!buildingType.isEmpty() && !supersedes.contains(buildingType)) supersedes.add(buildingType);
            }
        }
        GROUPS.add(new GroupDef(id, label, matchPrefix, layout, tierPrefix, priority, supersedes));
    }

    private static void loadOverride(String buildingType, JsonObject json) {
        Optional<ResourceLocation> nodeItem = json.has("node_item")
                ? resolveNodeItem(json.get("node_item")) : Optional.empty();
        boolean hide = GsonHelper.getAsBoolean(json, "hide", false)
                || (json.has("node_item") && nodeItem.isEmpty());
        putOverride(buildingType, new BuildingOverride(nodeItem, hide), true);
        if (json.has("townsteadSpirit")) {
            Map<String, Integer> spirit = parseSpiritMap(json.getAsJsonObject("townsteadSpirit"), null);
            if (!spirit.isEmpty()) BuildingSpiritIndex.put(buildingType, spirit);
        }
    }

    /**
     * Resolves {@code node_item}: a single item id or a list of candidates, first one present
     * in the item registry wins (so per-mod icon variants degrade gracefully). Empty when
     * nothing resolves — callers treat a specified-but-unresolvable icon as {@code hide},
     * since a building whose signature item doesn't exist shouldn't be offered.
     */
    private static Optional<ResourceLocation> resolveNodeItem(JsonElement element) {
        List<JsonElement> candidates = element.isJsonArray()
                ? element.getAsJsonArray().asList() : List.of(element);
        for (JsonElement candidate : candidates) {
            ResourceLocation id = ResourceLocation.tryParse(GsonHelper.convertToString(candidate, "node_item"));
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) return Optional.of(id);
        }
        return Optional.empty();
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
                int iconU = GsonHelper.getAsInt(json, "iconU", 0);
                int iconV = GsonHelper.getAsInt(json, "iconV", 0);
                if (GsonHelper.getAsBoolean(json, "icon", false) || iconU != 0 || iconV != 0) {
                    // MCA exposes atlas coordinates after applying these scale factors.
                    BuildingIconResolver.registerBuildingTypeIcon(buildingType, iconU * 20, iconV * 60);
                }
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
        if (blocks.isEmpty()) {
            // No blocks map means the MCA building type isn't loaded; a spec with zero
            // requirements would classify every enclosure as this type.
            LOGGER.warn("Skipped enclosure type '{}': building type has no blocks map", buildingType);
            return;
        }
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
            if (!ModCompat.isCompatAvailable(buildingType)) continue;
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
            if (!ModCompat.isCompatAvailable(buildingType)) continue;
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
     * vanilla. Blocks: {@code catalog} (node_item/hide), {@code spirit}, {@code spawn}; the concise
     * {@code enclosure} string selects required/optional/none physical enclosure, and
     * {@code dialogue.topics} declares the village-life subjects this place makes available. The legacy object
     * form of {@code enclosure} remains the fenced-area classifier and derives perimeter/interior
     * from the MCA {@code blocks} map cached in {@code blocksByType}.
     */
    private static void scanExtendedBuildings(ResourceManager resourceManager,
            Map<String, Map<String, Integer>> blocksByType, Map<String, Integer> priorityByType,
            Map<String, BuildingSpawnPolicy> spawnPolicies,
            Map<String, List<ResourceLocation>> workersByType,
            Map<String, BuildingEnclosurePolicies.Mode> enclosurePolicies,
            Map<String, Set<String>> dialogueTopicsByType) {
        Map<ResourceLocation, Resource> resources = resourceManager.listResources("extended_buildings",
                id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            String path = location.getPath();
            if (!path.startsWith("extended_buildings/") || !path.endsWith(".json")) continue;
            String buildingType = path.substring("extended_buildings/".length(), path.length() - ".json".length());
            // compat/<mod>/ sidecars ship ungated in the jar (only the mca building_types are
            // served conditionally), so gate here or absent-mod buildings still register data —
            // worst case an enclosure spec with an empty blocks map that matches any pen.
            if (!ModCompat.isCompatAvailable(buildingType)) continue;
            try (InputStream in = entry.getValue().open();
                    InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json == null) continue;
                TownsteadSchema.validate(json, "townstead:extended_building/v1");
                if (json.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(json.get("mods")))) {
                    // The MCA building type can still exist with optional/empty block tags, but it
                    // must not leak into Townstead's catalog or workforce when its provider is absent.
                    putOverride(buildingType, new BuildingOverride(Optional.empty(), true), true);
                    continue;
                }

                if (json.has("catalog") && json.get("catalog").isJsonObject()) {
                    JsonObject cat = json.getAsJsonObject("catalog");
                    Optional<ResourceLocation> nodeItem = cat.has("node_item")
                            ? resolveNodeItem(cat.get("node_item")) : Optional.empty();
                    boolean hide = GsonHelper.getAsBoolean(cat, "hide", false)
                            || (cat.has("node_item") && nodeItem.isEmpty());
                    putOverride(buildingType, new BuildingOverride(nodeItem, hide), true);
                }
                if (json.has("spirit") && json.get("spirit").isJsonObject()) {
                    Map<String, Integer> spirit = parseSpiritMap(json.getAsJsonObject("spirit"), location);
                    if (!spirit.isEmpty()) BuildingSpiritIndex.put(buildingType, spirit);
                }
                if (json.has("spawn") && json.get("spawn").isJsonObject()) {
                    spawnPolicies.put(buildingType, BuildingSpawnPolicy.parse(json.getAsJsonObject("spawn")));
                }
                if (json.has("workers") && json.get("workers").isJsonArray()) {
                    List<ResourceLocation> workers = new java.util.ArrayList<>();
                    for (JsonElement element : json.getAsJsonArray("workers")) {
                        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) continue;
                        ResourceLocation profession = ResourceLocation.tryParse(element.getAsString());
                        if (profession != null && !workers.contains(profession)) workers.add(profession);
                    }
                    workersByType.put(buildingType, List.copyOf(workers));
                }
                if (json.has("dialogue")) {
                    if (!json.get("dialogue").isJsonObject()) {
                        throw new IllegalArgumentException("'dialogue' must be an object");
                    }
                    JsonObject dialogue = json.getAsJsonObject("dialogue");
                    if (dialogue.has("topics")) {
                        if (!dialogue.get("topics").isJsonArray()) {
                            throw new IllegalArgumentException("'dialogue.topics' must be an array");
                        }
                        Set<String> topics = new java.util.LinkedHashSet<>();
                        for (JsonElement element : dialogue.getAsJsonArray("topics")) {
                            if (!element.isJsonPrimitive()
                                    || !element.getAsJsonPrimitive().isString()
                                    || element.getAsString().isBlank()) {
                                throw new IllegalArgumentException(
                                        "'dialogue.topics' entries must be non-empty strings");
                            }
                            topics.add(element.getAsString());
                        }
                        if (!topics.isEmpty()) dialogueTopicsByType.put(buildingType, Set.copyOf(topics));
                    }
                }
                if (json.has("enclosure")) {
                    JsonElement enclosure = json.get("enclosure");
                    if (enclosure.isJsonPrimitive() && enclosure.getAsJsonPrimitive().isString()) {
                        BuildingEnclosurePolicies.Mode mode = BuildingEnclosurePolicies.Mode.parse(
                                enclosure.getAsString());
                        if (mode != BuildingEnclosurePolicies.Mode.REQUIRED) {
                            enclosurePolicies.put(buildingType, mode);
                        }
                    } else if (enclosure.isJsonObject()) {
                        // Legacy fenced-enclosure classifier. Its object shape remains supported;
                        // the new physical-form policy is intentionally the concise string form.
                        JsonObject enc = enclosure.getAsJsonObject();
                        Map<String, Integer> blocks = blocksByType.getOrDefault(buildingType, Map.of());
                        registerEnclosure(buildingType, blocks, priorityByType.getOrDefault(buildingType, 0),
                                GsonHelper.getAsInt(enc, "minInterior", 4),
                                GsonHelper.getAsInt(enc, "maxInterior", 1024));
                    } else {
                        throw new IllegalArgumentException("'enclosure' must be a policy string or an object");
                    }
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

    /** Copy of the override map, for the catalog sync packet. */
    public static Map<String, BuildingOverride> overridesSnapshot() {
        synchronized (OVERRIDES) {
            return new LinkedHashMap<>(OVERRIDES);
        }
    }

    /** The datapack-provided theme, before any client resource-pack theme is merged in. */
    public static Theme dataTheme() {
        return DATA_THEME;
    }

    /**
     * Client-side entry point for {@link CatalogSyncS2CPayload}: replace everything the
     * server's datapack reload produced. On a dedicated server the client never runs
     * {@link #apply}, so groups, overrides, theme, and spirits stay empty without this.
     */
    public static void applySynced(CatalogSyncS2CPayload payload) {
        GROUPS.clear();
        GROUPS.addAll(payload.groups());
        MATCH_CACHE.clear();
        synchronized (OVERRIDES) {
            OVERRIDES.clear();
            OVERRIDES.putAll(payload.overrides());
        }
        DATA_THEME = payload.theme();
        THEME = payload.theme();
        CLIENT_THEME_RESOURCE_MANAGER = null;
        BuildingSpiritIndex.replaceAll(payload.spirits());
        BuildingIconResolver.invalidate();
        com.aetherianartificer.townstead.compat.mca.McaBuildingDiscovery.invalidateSignatures();
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

    /**
     * Fallback building types hidden by groups that actually have at least one available member.
     * Recognition and saved village buildings are deliberately unaffected; this is presentation
     * substitution, not destructive migration.
     */
    public static Set<String> activeSupersededBuildingTypes(Collection<String> availableBuildingTypes) {
        return activeSupersededBuildingTypes(availableBuildingTypes, GROUPS);
    }

    /** True when an installed provider currently replaces this fallback building type. */
    public static boolean isActiveSupersededBuildingType(String buildingType) {
        return buildingType != null && activeSupersededBuildingTypes(availableBuildingTypeNames())
                .contains(buildingType);
    }

    /**
     * Removes building types superseded by an installed provider while preserving MCA's
     * original candidate order. This is shared by MCA surfaces outside Townstead's catalog,
     * such as the building-polymorph chooser.
     */
    public static List<String> withoutActiveSupersededBuildingTypes(Collection<String> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<String> available = availableBuildingTypeNames();
        return withoutActiveSupersededBuildingTypes(candidates, available, GROUPS);
    }

    /**
     * Authoritative recognition filter. Unlike the polymorph-screen helper, this deliberately
     * permits an empty result: if only a superseded fallback matches the room, MCA must reject
     * the room instead of silently creating the obsolete building type.
     */
    public static List<String> withoutActiveSupersededBuildingTypesForRecognition(
            Collection<String> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        return withoutActiveSupersededBuildingTypesStrict(candidates, availableBuildingTypeNames(), GROUPS);
    }

    private static List<String> availableBuildingTypeNames() {
        return BuildingTypes.getInstance().getBuildingTypes().values().stream()
                .filter(BuildingType::visible)
                .filter(type -> ModCompat.isCompatAvailable(type.name()))
                .filter(type -> !overrideFor(type.name()).hide())
                .map(BuildingType::name)
                .toList();
    }

    static List<String> withoutActiveSupersededBuildingTypes(
            Collection<String> candidates,
            Collection<String> availableBuildingTypes,
            Collection<GroupDef> groups) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        Set<String> superseded = activeSupersededBuildingTypes(availableBuildingTypes, groups);
        if (superseded.isEmpty()) return List.copyOf(candidates);
        List<String> filtered = candidates.stream()
                .filter(type -> !superseded.contains(type))
                .toList();
        // Never turn MCA's chooser into an unusable empty screen if a malformed data pack
        // declares every matching type superseded.
        return filtered.isEmpty() ? List.copyOf(candidates) : filtered;
    }

    static List<String> withoutActiveSupersededBuildingTypesStrict(
            Collection<String> candidates,
            Collection<String> availableBuildingTypes,
            Collection<GroupDef> groups) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        Set<String> superseded = activeSupersededBuildingTypes(availableBuildingTypes, groups);
        if (superseded.isEmpty()) return List.copyOf(candidates);
        return candidates.stream()
                .filter(type -> !superseded.contains(type))
                .toList();
    }

    static Set<String> activeSupersededBuildingTypes(
            Collection<String> availableBuildingTypes,
            Collection<GroupDef> groups) {
        if (availableBuildingTypes == null || availableBuildingTypes.isEmpty()
                || groups == null || groups.isEmpty()) return Set.of();
        Set<String> superseded = new HashSet<>();
        for (GroupDef group : groups) {
            if (group.supersedes().isEmpty() || group.matchPrefix().isEmpty()) continue;
            boolean active = false;
            for (String buildingType : availableBuildingTypes) {
                if (buildingType != null && buildingType.startsWith(group.matchPrefix())) {
                    active = true;
                    break;
                }
            }
            if (active) superseded.addAll(group.supersedes());
        }
        return superseded.isEmpty() ? Set.of() : Set.copyOf(superseded);
    }
}
