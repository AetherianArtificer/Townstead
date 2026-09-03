package com.aetherianartificer.townstead.hangout;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.aetherianartificer.townstead.pheno.lang.normalize.PhenoNormalizer;
import com.aetherianartificer.townstead.performance.PerformanceRequest;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Versioned, transactional registry for all four hangout resource families. */
public final class HangoutData {
    public static final String VENUE_SCHEMA = "townstead:hangout_venue/v1";
    public static final String SPOT_SCHEMA = "townstead:hangout_spot/v1";
    public static final String ACTIVITY_SCHEMA = "townstead:hangout_activity/v1";
    public static final String POLICY_SCHEMA = "townstead:hangout_policy/v1";

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/Hangouts");
    private static volatile Map<ResourceLocation, HangoutVenue> venues = Map.of();
    private static volatile Map<ResourceLocation, HangoutSpot> spots = Map.of();
    private static volatile Map<ResourceLocation, HangoutActivity> activities = Map.of();
    private static volatile Map<ResourceLocation, HangoutPolicy> policies = Map.of();

    private HangoutData() {}

    public static Map<ResourceLocation, HangoutVenue> venues() { return venues; }
    public static Map<ResourceLocation, HangoutSpot> spots() { return spots; }
    public static Map<ResourceLocation, HangoutActivity> activities() { return activities; }
    public static Map<ResourceLocation, HangoutPolicy> policies() { return policies; }

    static HangoutVenue parseVenue(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validateRequired(json, VENUE_SCHEMA);
        requireOnly(json, "schema", "mods", "buildings", "capacity", "activities", "amenities", "open_when");
        Set<String> buildings = strings(json, "buildings", true);
        List<ResourceLocation> activityIds = idList(json, "activities", false);
        Condition open = condition(json, "open_when");
        return new HangoutVenue(id, buildings, positive(json, "capacity", 8), activityIds,
                strings(json, "amenities", false), open);
    }

    static HangoutSpot parseSpot(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validateRequired(json, SPOT_SCHEMA);
        requireOnly(json, "schema", "mods", "blocks", "posture", "adapter", "capacity", "canonical_offset", "linked_offsets",
                "linked_offsets_relative_to_facing", "embodiment_offset",
                "embodiment_offset_relative_to_facing", "available_when", "rest");
        Set<ResourceLocation> blocks = new LinkedHashSet<>();
        Set<ResourceLocation> tags = new LinkedHashSet<>();
        for (String selector : strings(json, "blocks", true)) {
            boolean tag = selector.startsWith("#");
            ResourceLocation parsed = ResourceLocation.tryParse(tag ? selector.substring(1) : selector);
            if (parsed == null) throw new IllegalArgumentException("invalid block selector '" + selector + "'");
            (tag ? tags : blocks).add(parsed);
        }
        ResourceLocation posture = requiredId(json, "posture");
        ResourceLocation adapter = json.has("adapter") ? requiredId(json, "adapter") : HangoutEmbodiment.VANILLA;
        BlockPos canonical = offset(json.get("canonical_offset"), "canonical_offset");
        Set<BlockPos> linked = new LinkedHashSet<>();
        if (json.has("linked_offsets")) {
            if (!json.get("linked_offsets").isJsonArray()) throw new IllegalArgumentException("linked_offsets must be an array");
            int index = 0;
            for (JsonElement element : json.getAsJsonArray("linked_offsets")) {
                linked.add(offset(element, "linked_offsets[" + index++ + "]"));
            }
        }
        BlockCondition available = json.has("available_when") ? BlockConditions.parse(json.get("available_when")) : null;
        if (json.has("available_when") && available == null) {
            throw new IllegalArgumentException("available_when is not a valid Pheno block condition");
        }
        HangoutSpot.RestBonus rest = null;
        if (json.has("rest")) {
            if (!json.get("rest").isJsonObject()) throw new IllegalArgumentException("rest must be an object");
            JsonObject value = json.getAsJsonObject("rest");
            requireOnly(value, "fatigue_recovery");
            rest = new HangoutSpot.RestBonus(GsonHelper.getAsFloat(value, "fatigue_recovery"));
        }
        Vec3 embodimentOffset = vector(json.get("embodiment_offset"), "embodiment_offset",
                new Vec3(0D, 0.05D, 0D));
        return new HangoutSpot(id, blocks, tags, posture, adapter, positive(json, "capacity", 1), canonical, linked,
                GsonHelper.getAsBoolean(json, "linked_offsets_relative_to_facing", false), embodimentOffset,
                GsonHelper.getAsBoolean(json, "embodiment_offset_relative_to_facing", false), available, rest);
    }

    static HangoutActivity parseActivity(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validateRequired(json, ACTIVITY_SCHEMA);
        requireOnly(json, "schema", "mods", "kind", "minimum_participants", "maximum_participants",
                "duration_ticks", "roles", "postures", "start_when", "continue_when",
                "service_when", "on_start", "on_tick", "on_finish", "on_service_accepted",
                "on_service_refused", "on_service_missing", "service", "performance");
        int min = positive(json, "minimum_participants", 2);
        int max = positive(json, "maximum_participants", Math.max(2, min));
        HangoutActivity.Kind kind;
        try {
            kind = HangoutActivity.Kind.valueOf(GsonHelper.getAsString(json, "kind", "socialize")
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown kind '" + GsonHelper.getAsString(json, "kind") + "'");
        }
        Map<String, Integer> roles = new LinkedHashMap<>();
        if (json.has("roles")) {
            if (!json.get("roles").isJsonObject()) throw new IllegalArgumentException("roles must be an object");
            for (Map.Entry<String, JsonElement> role : json.getAsJsonObject("roles").entrySet()) {
                if (!role.getValue().isJsonPrimitive() || !role.getValue().getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException("role '" + role.getKey() + "' count must be a number");
                }
                int count = role.getValue().getAsInt();
                if (count < 1) throw new IllegalArgumentException("role counts must be positive");
                roles.put(role.getKey(), count);
            }
        }
        List<HangoutActivity.ServiceCourse> serviceCourses = new ArrayList<>();
        if (json.has("service")) {
            if (!json.get("service").isJsonObject()) throw new IllegalArgumentException("service must be an object");
            JsonObject service = json.getAsJsonObject("service");
            requireOnly(service, "courses");
            if (!service.has("courses") || !service.get("courses").isJsonArray()) {
                throw new IllegalArgumentException("service.courses must be an array");
            }
            int index = 0;
            for (JsonElement element : service.getAsJsonArray("courses")) {
                if (!element.isJsonObject()) throw new IllegalArgumentException("service courses must be objects");
                JsonObject course = element.getAsJsonObject();
                requireOnly(course, "id", "kind", "role", "at_ticks", "lease_ticks");
                HangoutActivity.Kind courseKind;
                try {
                    courseKind = HangoutActivity.Kind.valueOf(GsonHelper.getAsString(course, "kind", "mixed")
                            .toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("unknown service course kind");
                }
                serviceCourses.add(new HangoutActivity.ServiceCourse(
                        GsonHelper.getAsString(course, "id", "course_" + index++), courseKind,
                        GsonHelper.getAsString(course, "role"),
                        Math.max(0, GsonHelper.getAsInt(course, "at_ticks", 0)),
                        positive(course, "lease_ticks", 100)));
            }
            Set<String> courseIds = new LinkedHashSet<>();
            for (HangoutActivity.ServiceCourse course : serviceCourses) {
                if (!roles.containsKey(course.role())) {
                    throw new IllegalArgumentException("service course '" + course.id()
                            + "' names role '" + course.role() + "' absent from roles");
                }
                if (!courseIds.add(course.id())) {
                    throw new IllegalArgumentException("duplicate service course id '" + course.id() + "'");
                }
            }
        }
        HangoutActivity.Performance performance = null;
        if (json.has("performance")) {
            if (!json.get("performance").isJsonObject()) throw new IllegalArgumentException("performance must be an object");
            JsonObject value = json.getAsJsonObject("performance");
            requireOnly(value, "id", "channel", "duration_ticks", "priority", "fallback");
            PerformanceRequest.Fallback fallback;
            try {
                fallback = PerformanceRequest.Fallback.valueOf(GsonHelper.getAsString(value, "fallback", "vanilla_gesture")
                        .toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("unknown performance fallback");
            }
            performance = new HangoutActivity.Performance(requiredId(value, "id"),
                    GsonHelper.getAsString(value, "channel", "social"),
                    positive(value, "duration_ticks", positive(json, "duration_ticks", 240)),
                    GsonHelper.getAsInt(value, "priority", 0), fallback);
        }
        return new HangoutActivity(id, kind, min, max, positive(json, "duration_ticks", 240), roles,
                ids(json, "postures", false), condition(json, "start_when"),
                condition(json, "continue_when"), condition(json, "service_when"),
                action(json, "on_start"), action(json, "on_tick"), action(json, "on_finish"),
                action(json, "on_service_accepted"), action(json, "on_service_refused"),
                action(json, "on_service_missing"), serviceCourses, performance);
    }

    static HangoutPolicy parsePolicy(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validateRequired(json, POLICY_SCHEMA);
        requireOnly(json, "schema", "mods", "minimum_group", "maximum_group", "solo_fallback",
                "invite_radius", "venue_radius", "cooldown_ticks", "arrival_timeout_ticks",
                "lease_ticks", "bond_weights", "initiator_when", "companion_when");
        Map<String, Integer> weights = new LinkedHashMap<>();
        if (json.has("bond_weights")) {
            if (!json.get("bond_weights").isJsonObject()) throw new IllegalArgumentException("bond_weights must be an object");
            for (Map.Entry<String, JsonElement> weight : json.getAsJsonObject("bond_weights").entrySet()) {
                if (!weight.getValue().isJsonPrimitive() || !weight.getValue().getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException("bond weight '" + weight.getKey() + "' must be a number");
                }
                weights.put(weight.getKey(), weight.getValue().getAsInt());
            }
        }
        int timeout = positive(json, "arrival_timeout_ticks", 200);
        return new HangoutPolicy(id, positive(json, "minimum_group", 2), positive(json, "maximum_group", 4),
                GsonHelper.getAsBoolean(json, "solo_fallback", false), positive(json, "invite_radius", 24),
                positive(json, "venue_radius", 64), Math.max(0, GsonHelper.getAsInt(json, "cooldown_ticks", 1200)),
                timeout, positive(json, "lease_ticks", timeout + 600), weights,
                condition(json, "initiator_when"), condition(json, "companion_when"));
    }

    private static @Nullable Condition condition(JsonObject json, String key) {
        if (!json.has(key)) return null;
        if (!json.get(key).isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        Condition parsed = Conditions.parse(PhenoNormalizer.normalizeCondition(json.getAsJsonObject(key)));
        if (parsed == null) throw new IllegalArgumentException(key + " is not a valid Pheno condition");
        return parsed;
    }

    private static @Nullable Action action(JsonObject json, String key) {
        if (!json.has(key)) return null;
        Action parsed = Actions.parse(PhenoNormalizer.normalizeAction(json.get(key)));
        if (parsed == null) throw new IllegalArgumentException(key + " is not a valid Pheno action");
        return parsed;
    }

    private static int positive(JsonObject json, String key, int fallback) {
        int value = GsonHelper.getAsInt(json, key, fallback);
        if (value < 1) throw new IllegalArgumentException(key + " must be positive");
        return value;
    }

    private static ResourceLocation requiredId(JsonObject json, String key) {
        ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(json, key));
        if (id == null) throw new IllegalArgumentException(key + " must be a resource id");
        return id;
    }

    private static Set<ResourceLocation> ids(JsonObject json, String key, boolean required) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (String value : strings(json, key, required)) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null) throw new IllegalArgumentException(key + " contains invalid id '" + value + "'");
            out.add(id);
        }
        return Set.copyOf(out);
    }

    private static List<ResourceLocation> idList(JsonObject json, String key, boolean required) {
        if (!json.has(key)) {
            if (required) throw new IllegalArgumentException(key + " is required");
            return List.of();
        }
        if (!json.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        List<ResourceLocation> out = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
                    || element.getAsString().isBlank()) {
                throw new IllegalArgumentException(key + " must contain non-blank strings");
            }
            String value = element.getAsString();
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null) throw new IllegalArgumentException(key + " contains invalid id '" + value + "'");
            if (!out.contains(id)) out.add(id);
        }
        if (required && out.isEmpty()) throw new IllegalArgumentException(key + " must not be empty");
        return List.copyOf(out);
    }

    private static Set<String> strings(JsonObject json, String key, boolean required) {
        if (!json.has(key)) {
            if (required) throw new IllegalArgumentException(key + " is required");
            return Set.of();
        }
        if (!json.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        Set<String> out = new LinkedHashSet<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
                    || element.getAsString().isBlank()) {
                throw new IllegalArgumentException(key + " must contain non-blank strings");
            }
            out.add(element.getAsString());
        }
        if (required && out.isEmpty()) throw new IllegalArgumentException(key + " must not be empty");
        return Set.copyOf(out);
    }

    private static BlockPos offset(@Nullable JsonElement element, String path) {
        if (element == null) return new BlockPos(0, 0, 0);
        if (!element.isJsonArray()) throw new IllegalArgumentException(path + " must be [x,y,z]");
        JsonArray array = element.getAsJsonArray();
        if (array.size() != 3) throw new IllegalArgumentException(path + " must contain exactly three integers");
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }

    private static Vec3 vector(@Nullable JsonElement element, String path, Vec3 fallback) {
        if (element == null) return fallback;
        if (!element.isJsonArray()) throw new IllegalArgumentException(path + " must be [x,y,z]");
        JsonArray array = element.getAsJsonArray();
        if (array.size() != 3) throw new IllegalArgumentException(path + " must contain exactly three numbers");
        double x = array.get(0).getAsDouble();
        double y = array.get(1).getAsDouble();
        double z = array.get(2).getAsDouble();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException(path + " must contain finite numbers");
        }
        return new Vec3(x, y, z);
    }

    private static void requireOnly(JsonObject json, String... allowed) {
        Set<String> keys = Set.of(allowed);
        for (String key : json.keySet()) {
            if (!keys.contains(key)) throw new IllegalArgumentException("unknown field '" + key + "'");
        }
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<String, Map<ResourceLocation, JsonObject>>> {
        private static final List<String> FAMILIES = List.of("hangout_venue", "hangout_spot", "hangout_activity", "hangout_policy");

        @Override
        protected Map<String, Map<ResourceLocation, JsonObject>> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<String, Map<ResourceLocation, JsonObject>> loaded = new LinkedHashMap<>();
            for (String family : FAMILIES) {
                Map<ResourceLocation, JsonObject> documents = new LinkedHashMap<>();
                for (Map.Entry<ResourceLocation, Resource> entry : manager
                        .listResources(family, path -> path.getPath().endsWith(".json")).entrySet()) {
                    ResourceLocation file = entry.getKey();
                    String path = file.getPath();
                    ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":"
                            + path.substring(family.length() + 1, path.length() - 5));
                    if (id == null) continue;
                    try (Reader reader = entry.getValue().openAsReader()) {
                        JsonElement element = JsonParser.parseReader(reader);
                        if (!element.isJsonObject()) throw new IllegalArgumentException("document root must be an object");
                        documents.put(id, element.getAsJsonObject());
                    } catch (Exception exception) {
                        LOGGER.warn("Failed to read {}: {}", file, exception.getMessage());
                    }
                }
                loaded.put(family, documents);
            }
            return loaded;
        }

        @Override
        protected void apply(Map<String, Map<ResourceLocation, JsonObject>> prepared, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, HangoutVenue> nextVenues = parseFamily(prepared.get("hangout_venue"), HangoutData::parseVenue);
            Map<ResourceLocation, HangoutSpot> nextSpots = parseFamily(prepared.get("hangout_spot"), HangoutData::parseSpot);
            Map<ResourceLocation, HangoutActivity> nextActivities = parseFamily(prepared.get("hangout_activity"), HangoutData::parseActivity);
            Map<ResourceLocation, HangoutPolicy> nextPolicies = parseFamily(prepared.get("hangout_policy"), HangoutData::parsePolicy);
            venues = Map.copyOf(nextVenues);
            spots = Map.copyOf(nextSpots);
            activities = Map.copyOf(nextActivities);
            policies = Map.copyOf(nextPolicies);
            HangoutEngine.onReload();
            LOGGER.info("Loaded {} hangout venues, {} spots, {} activities, and {} policies",
                    venues.size(), spots.size(), activities.size(), policies.size());
        }

        private static <T> Map<ResourceLocation, T> parseFamily(Map<ResourceLocation, JsonObject> documents,
                                                                 Parser<T> parser) {
            Map<ResourceLocation, T> out = new LinkedHashMap<>();
            if (documents == null) return out;
            for (Map.Entry<ResourceLocation, JsonObject> entry : documents.entrySet()) {
                JsonObject json = entry.getValue();
                if (json.has("mods")) {
                    Boolean enabled = ModGate.evaluate(json.get("mods"));
                    if (enabled == null) {
                        LOGGER.warn("Hangout {} rejected: malformed mods gate", entry.getKey());
                        continue;
                    }
                    if (!enabled) continue;
                }
                try {
                    out.put(entry.getKey(), parser.parse(entry.getKey(), json));
                } catch (RuntimeException exception) {
                    LOGGER.warn("Hangout {} rejected: {}", entry.getKey(), exception.getMessage());
                }
            }
            return out;
        }

        @FunctionalInterface private interface Parser<T> { T parse(ResourceLocation id, JsonObject json); }
    }
}
