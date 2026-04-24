package com.aetherianartificer.townstead.client.catalog;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.enclosure.EnclosureTypeIndex;
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

    public record GroupDef(String id, String label, String matchPrefix, String layout, String tierPrefix, int priority) {
    }

    public record BuildingOverride(Optional<ResourceLocation> nodeItem, boolean hide) {
        public static final BuildingOverride EMPTY = new BuildingOverride(Optional.empty(), false);
    }

    private static final List<GroupDef> GROUPS = new CopyOnWriteArrayList<>();
    private static final Map<String, BuildingOverride> OVERRIDES = new LinkedHashMap<>();

    public CatalogDataLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        GROUPS.clear();
        synchronized (OVERRIDES) {
            OVERRIDES.clear();
        }
        BuildingSpiritIndex.clear();
        EnclosureTypeIndex.clear();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation location = entry.getKey();
            String path = location.getPath();
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "catalog entry");
                if (path.startsWith("groups/")) {
                    String id = location.getNamespace() + ":" + path.substring("groups/".length());
                    loadGroup(id, json);
                } else if (path.startsWith("buildings/")) {
                    String buildingType = path.substring("buildings/".length());
                    loadOverride(buildingType, json);
                }
            } catch (Exception ex) {
                LOGGER.warn("Rejected catalog entry '{}': {}", location, ex.getMessage());
            }
        }

        scanLegacyBuildingTypes(resourceManager);
        scanSpiritCompanions(resourceManager);

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

    private static void scanLegacyBuildingTypes(ResourceManager resourceManager) {
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
                    parseEnclosureSpec(buildingType, json, location);
                }
            } catch (Exception ex) {
                LOGGER.debug("Skipped legacy building_type scan for '{}': {}", location, ex.getMessage());
            }
        }
    }

    /**
     * Pull a {@code townsteadEnclosure} spec out of a building_type JSON and
     * register it with {@link EnclosureTypeIndex}. Perimeter and interior
     * requirements are derived from the sibling {@code blocks} map: fences /
     * fence-gates / walls become perimeter requirements, everything else
     * becomes interior signatures that drive classification.
     */
    private static void parseEnclosureSpec(String buildingType, JsonObject json, ResourceLocation source) {
        JsonElement marker = json.get("townsteadEnclosure");
        if (marker == null) return;
        int minInterior = 4;
        int maxInterior = 1024;
        if (marker.isJsonObject()) {
            JsonObject enc = marker.getAsJsonObject();
            minInterior = GsonHelper.getAsInt(enc, "minInterior", minInterior);
            maxInterior = GsonHelper.getAsInt(enc, "maxInterior", maxInterior);
        }
        Map<String, Integer> blocks = new HashMap<>();
        if (json.has("blocks") && json.get("blocks").isJsonObject()) {
            JsonObject b = json.getAsJsonObject("blocks");
            for (Map.Entry<String, JsonElement> e : b.entrySet()) {
                try {
                    blocks.put(e.getKey(), e.getValue().getAsInt());
                } catch (Exception ex) {
                    LOGGER.warn("Invalid block count for '{}' in {}: {}", e.getKey(), source, ex.getMessage());
                }
            }
        }
        int priority = GsonHelper.getAsInt(json, "priority", 0);
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
                Map<String, Integer> spirit = parseSpiritMap(json.getAsJsonObject("townsteadSpirit"), location);
                if (!spirit.isEmpty()) BuildingSpiritIndex.put(buildingType, spirit);
            } catch (Exception ex) {
                LOGGER.debug("Skipped spirit companion scan for '{}': {}", location, ex.getMessage());
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

    public static Optional<GroupDef> matchGroup(String buildingType) {
        for (GroupDef g : GROUPS) {
            if (!g.matchPrefix().isEmpty() && buildingType.startsWith(g.matchPrefix()))
                return Optional.of(g);
        }
        return Optional.empty();
    }
}
