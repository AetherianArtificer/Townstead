package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * How many times each building may appear in one generated village, from
 * {@code data/<ns>/village_layout/<path>.json}. Template pools pick buildings by weight alone; a
 * building that reaches its maximum is skipped for the rest of that village, and one still short of
 * its minimum is tried before the others. {@code groups} put a range on several buildings at once,
 * such as every house.
 *
 * <p>The same file decides where the village may start: {@code max_water} is the largest share of
 * its ground that may be water, and {@code keep_apart} how many chunks it keeps from other culture
 * villages.</p>
 *
 * <p>A layout with {@code stages} grows its villages to different sizes, from a hamlet to a
 * metropolis. Each village rolls one stage by weight, the same stage every time for the same world
 * and place, and the stage sets how far its streets reach and the ranges of its buildings in place
 * of the top-level ones. A stage may leave the village without its wall.</p>
 *
 * <p>Buildings that would stand over water or off a cliff are left out of the village; those named
 * under {@code wet}, such as a fisherman's stilt hut, may stand over water.</p>
 *
 * <pre>{@code
 * {
 *   "schema": "townstead:village_layout/v1",
 *   "structure": "townstead_cavalrosia:village/town",
 *   "max": { "townstead_cavalrosia:village/town/rose_chapel": 1 },
 *   "min": { "townstead_cavalrosia:village/town/piazza_well": 1 },
 *   "groups": [{ "name": "house", "min": 4, "max": 12,
 *                "templates": ["townstead_cavalrosia:village/town/stucco_townhouse"] }],
 *   "stages": [{ "name": "hamlet", "weight": 2, "size": 3, "max_distance": 40,
 *                "max": {}, "min": {}, "groups": [], "enclosure": false }],
 *   "wet": ["townstead_cavalrosia:village/town/canal_house"],
 *   "max_water": 0.25,
 *   "keep_apart": 6
 * }
 * }</pre>
 */
public final class VillageLayouts {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/VillageLayouts");
    private static final String SCHEMA = "townstead:village_layout/v1";
    private static volatile Ranges ranges = Ranges.NONE;
    private static volatile Map<ResourceLocation, Site> siteByStructure = Map.of();
    private static volatile Map<ResourceLocation, List<Stage>> stagesByStructure = Map.of();
    private static final ThreadLocal<Stage> PLANNING = new ThreadLocal<>();

    /** Where a village may start: the share of water its ground may hold, and its distance from others. */
    public record Site(double maxWater, int keepApart) {
        public static final Site ANYWHERE = new Site(1.0, 0);
    }

    private VillageLayouts() {}

    private static volatile Set<ResourceLocation> structures = Set.of();
    private static volatile Set<ResourceLocation> wetTemplates = Set.of();

    /** Whether a structure has a village layout, which makes it a culture village. */
    public static boolean isCultureVillage(@Nullable ResourceLocation structure) {
        return structure != null && structures.contains(structure);
    }

    /** Whether a building template is meant to stand in water, such as a fisherman's stilt hut. */
    public static boolean wet(@Nullable ResourceLocation template) {
        return template != null && wetTemplates.contains(template);
    }

    /** The site rules for a village structure; any site when its layout sets none. */
    public static Site site(@Nullable ResourceLocation structure) {
        return structure == null ? Site.ANYWHERE : siteByStructure.getOrDefault(structure, Site.ANYWHERE);
    }

    static void replaceSites(Map<ResourceLocation, Site> sites) {
        siteByStructure = Map.copyOf(sites);
    }

    /**
     * A wall around a village: block ids from the ground up, an optional gate block for the openings,
     * how far it stands outside the outermost buildings and streets, how many gates it has and how
     * wide, and an optional structure template set at its corners, with the layer of that template
     * that meets the ground.
     */
    public record Enclosure(List<String> wall, @Nullable String gate, int margin, int gates, int gateWidth,
                            @Nullable ResourceLocation corner, int cornerGround) {
        public Enclosure {
            wall = List.copyOf(wall);
        }
    }

    private static volatile Map<ResourceLocation, Enclosure> enclosureByStructure = Map.of();

    /** The wall a village structure is built inside, or null for an open village. */
    public static @Nullable Enclosure enclosure(@Nullable ResourceLocation structure) {
        return structure == null ? null : enclosureByStructure.get(structure);
    }

    /** The building ranges of a village: the most and fewest copies of each template, and the groups. */
    public record Ranges(Map<ResourceLocation, Integer> max, Map<ResourceLocation, Integer> min,
                         Map<ResourceLocation, List<Group>> groups) {
        static final Ranges NONE = new Ranges(Map.of(), Map.of(), Map.of());

        public Ranges {
            max = Map.copyOf(max);
            min = Map.copyOf(min);
            groups = Map.copyOf(groups);
        }

        static Ranges of(Map<ResourceLocation, Integer> max, Map<ResourceLocation, Integer> min, List<Group> groups) {
            Map<ResourceLocation, List<Group>> index = new LinkedHashMap<>();
            for (Group group : groups) {
                for (ResourceLocation template : group.templates()) index.computeIfAbsent(template, k -> new ArrayList<>()).add(group);
            }
            Map<ResourceLocation, List<Group>> copy = new LinkedHashMap<>();
            index.forEach((template, list) -> copy.put(template, List.copyOf(list)));
            return new Ranges(max, min, copy);
        }

        boolean isEmpty() {
            return max.isEmpty() && min.isEmpty() && groups.isEmpty();
        }
    }

    /**
     * One size a village may grow to: its name, how often villages roll it, the jigsaw depth and reach
     * of its streets, its building ranges, and whether it is walled when its layout has a wall.
     */
    public record Stage(String name, int weight, int size, int maxDistance, Ranges ranges, boolean walled) {}

    /** The stages a village structure may roll, smallest first; empty when it has none. */
    public static List<Stage> stages(@Nullable ResourceLocation structure) {
        return structure == null ? List.of() : stagesByStructure.getOrDefault(structure, List.of());
    }

    /**
     * The stage of the village a structure starts in one chunk of a world, or null when the structure
     * has no stages. The same world, chunk and structure always give the same stage, so the stage of a
     * standing village can be worked out again later.
     */
    public static @Nullable Stage stage(@Nullable ResourceLocation structure, long seed, int chunkX, int chunkZ) {
        List<Stage> stages = stages(structure);
        int total = 0;
        for (Stage stage : stages) total += stage.weight();
        if (total <= 0) return null;
        long mixed = seed ^ (chunkX * 0x9E3779B97F4A7C15L) ^ (chunkZ * 0xC2B2AE3D27D4EB4FL) ^ structure.hashCode();
        mixed = (mixed ^ (mixed >>> 33)) * 0xFF51AFD7ED558CCDL;
        mixed = (mixed ^ (mixed >>> 33)) * 0xC4CEB9FE1A85EC53L;
        int roll = (int) Math.floorMod(mixed ^ (mixed >>> 33), (long) total);
        for (Stage stage : stages) {
            roll -= stage.weight();
            if (roll < 0) return stage;
        }
        return stages.get(stages.size() - 1);
    }

    /** Marks the village being planned on this thread, whose stage then sets its reach and ranges. */
    public static void beginPlanning(@Nullable Stage stage) {
        if (stage == null) PLANNING.remove();
        else PLANNING.set(stage);
    }

    public static void endPlanning() {
        PLANNING.remove();
    }

    /** The stage of the village being planned on this thread, or null. */
    public static @Nullable Stage planning() {
        return PLANNING.get();
    }

    /** The most copies of a structure template one village may hold, or null when unlimited. */
    public static @Nullable Integer max(@Nullable ResourceLocation template) {
        return template == null ? null : active().max().get(template);
    }

    public static boolean hasLimits() {
        return !active().isEmpty();
    }

    private static Ranges active() {
        Stage stage = PLANNING.get();
        return stage != null ? stage.ranges() : ranges;
    }

    static void replace(Map<ResourceLocation, Integer> limits) {
        replace(limits, Map.of(), List.of());
    }

    static void replace(Map<ResourceLocation, Integer> limits, Map<ResourceLocation, Integer> minimums,
                        List<Group> groups) {
        ranges = Ranges.of(limits, minimums, groups);
    }

    static void replaceStages(Map<ResourceLocation, List<Stage>> stages) {
        Map<ResourceLocation, List<Stage>> copy = new LinkedHashMap<>();
        stages.forEach((structure, list) -> copy.put(structure, List.copyOf(list)));
        stagesByStructure = Map.copyOf(copy);
    }

    /** A range over several buildings at once, such as every house of a village. */
    public record Group(String name, Set<ResourceLocation> templates, int min, @Nullable Integer max) {
        public Group {
            templates = Set.copyOf(templates);
        }

        int count(Map<ResourceLocation, Integer> placed) {
            int total = 0;
            for (ResourceLocation template : templates) total += placed.getOrDefault(template, 0);
            return total;
        }
    }

    /**
     * Orders the buildings a village tries next: those it may not place any more are dropped, those
     * still short of their own or their group's minimum come first, nearest to meeting it first, and
     * the rest follow, each part otherwise in the order given. A village short on room then still
     * gets its one temple before its fourth house. {@code placed} counts the buildings already in the
     * village by template. The village being planned uses its stage's ranges when it has one.
     */
    public static <T> List<T> arrange(List<T> candidates, Function<T, ResourceLocation> template,
                                      Map<ResourceLocation, Integer> placed) {
        Ranges limits = active();
        List<T> needed = new ArrayList<>();
        Map<T, Integer> deficits = new java.util.IdentityHashMap<>();
        List<T> rest = new ArrayList<>();
        for (T candidate : candidates) {
            ResourceLocation id = template.apply(candidate);
            if (id == null) {
                rest.add(candidate);
                continue;
            }
            int count = placed.getOrDefault(id, 0);
            Integer max = limits.max().get(id);
            if (max != null && count >= max) continue;
            List<Group> groups = limits.groups().getOrDefault(id, List.of());
            boolean full = false;
            int deficit = Integer.MAX_VALUE;
            int own = limits.min().getOrDefault(id, 0) - count;
            if (own > 0) deficit = own;
            for (Group group : groups) {
                int total = group.count(placed);
                if (group.max() != null && total >= group.max()) full = true;
                if (total < group.min()) deficit = Math.min(deficit, group.min() - total);
            }
            if (full) continue;
            if (deficit == Integer.MAX_VALUE) {
                rest.add(candidate);
            } else {
                needed.add(candidate);
                deficits.put(candidate, deficit);
            }
        }
        if (needed.isEmpty()) return rest;
        needed.sort(java.util.Comparator.comparingInt(deficits::get));
        needed.addAll(rest);
        return needed;
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        private static final Gson GSON = new Gson();

        public Loader() {
            super(GSON, "village_layout");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resources,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, Integer> limits = new LinkedHashMap<>();
            Map<ResourceLocation, Integer> minimums = new LinkedHashMap<>();
            List<Group> groups = new ArrayList<>();
            Map<ResourceLocation, Site> sites = new LinkedHashMap<>();
            Map<ResourceLocation, Enclosure> enclosures = new LinkedHashMap<>();
            Map<ResourceLocation, List<Stage>> stages = new LinkedHashMap<>();
            Set<ResourceLocation> villages = new java.util.LinkedHashSet<>();
            Set<ResourceLocation> wet = new java.util.LinkedHashSet<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                    TownsteadSchema.validate(root, SCHEMA);
                    if (root.has("structure")) villages.add(structure(root));
                    for (JsonElement element : GsonHelper.getAsJsonArray(root, "wet", new JsonArray())) {
                        wet.add(template(GsonHelper.convertToString(element, "wet template")));
                    }
                    if (root.has("structure") && (root.has("max_water") || root.has("keep_apart"))) {
                        ResourceLocation structure = structure(root);
                        double water = GsonHelper.getAsDouble(root, "max_water", 1.0);
                        int apart = GsonHelper.getAsInt(root, "keep_apart", 0);
                        if (water < 0 || water > 1) throw new IllegalArgumentException("'max_water' must be between 0 and 1");
                        if (apart < 0 || apart > 32) throw new IllegalArgumentException("'keep_apart' must be between 0 and 32 chunks");
                        sites.put(structure, new Site(water, apart));
                    }
                    if (root.has("structure") && root.has("enclosure")) {
                        enclosures.put(structure(root), enclosure(GsonHelper.getAsJsonObject(root, "enclosure")));
                    }
                    if (root.has("structure") && root.has("stages")) {
                        List<Stage> list = new ArrayList<>();
                        for (JsonElement element : GsonHelper.getAsJsonArray(root, "stages")) {
                            list.add(stage(GsonHelper.convertToJsonObject(element, "stage")));
                        }
                        if (!list.isEmpty()) stages.put(structure(root), list);
                    }
                    limits.putAll(counts(GsonHelper.getAsJsonObject(root, "max", new JsonObject())));
                    minimums.putAll(counts(GsonHelper.getAsJsonObject(root, "min", new JsonObject())));
                    groups.addAll(groups(root));
                } catch (RuntimeException error) {
                    LOGGER.warn("Village layout {} rejected: {}", entry.getKey(), error.getMessage());
                }
            }
            replace(limits, minimums, groups);
            replaceSites(sites);
            replaceStages(stages);
            enclosureByStructure = Map.copyOf(enclosures);
            structures = Set.copyOf(villages);
            wetTemplates = Set.copyOf(wet);
            LOGGER.info("Loaded village layouts: {} maximum(s), {} minimum(s), {} group(s), {} site rule(s), {} staged type(s)",
                    limits.size(), minimums.size(), groups.size(), sites.size(), stages.size());
        }

        private static ResourceLocation structure(JsonObject root) {
            ResourceLocation structure = DataPackLang.parseId(GsonHelper.getAsString(root, "structure"));
            if (structure == null) throw new IllegalArgumentException("'structure' is not a structure id");
            return structure;
        }

        private static List<Group> groups(JsonObject json) {
            List<Group> out = new ArrayList<>();
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "groups", new JsonArray())) {
                out.add(group(GsonHelper.convertToJsonObject(element, "group")));
            }
            return out;
        }

        static Stage stage(JsonObject json) {
            String name = GsonHelper.getAsString(json, "name");
            int weight = GsonHelper.getAsInt(json, "weight", 1);
            int size = GsonHelper.getAsInt(json, "size");
            int reach = GsonHelper.getAsInt(json, "max_distance");
            if (weight < 0) throw new IllegalArgumentException("Stage " + name + " has a negative weight");
            if (size < 0 || size > 20) throw new IllegalArgumentException("Stage " + name + " size must be 0 to 20");
            // Worldgen looks for a structure's start at most eight chunks away.
            if (reach < 1 || reach > 128) throw new IllegalArgumentException("Stage " + name + " max_distance must be 1 to 128");
            Ranges stageRanges = Ranges.of(counts(GsonHelper.getAsJsonObject(json, "max", new JsonObject())),
                    counts(GsonHelper.getAsJsonObject(json, "min", new JsonObject())), groups(json));
            return new Stage(name, weight, size, reach, stageRanges, GsonHelper.getAsBoolean(json, "enclosure", true));
        }

        private static Map<ResourceLocation, Integer> counts(JsonObject object) {
            Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                out.put(template(entry.getKey()), count(entry.getValue(), entry.getKey()));
            }
            return out;
        }

        static Enclosure enclosure(JsonObject json) {
            List<String> layers = new ArrayList<>();
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "wall")) layers.add(blockId(element.getAsString()));
            if (layers.isEmpty() || layers.size() > 8) throw new IllegalArgumentException("'enclosure.wall' needs 1 to 8 blocks");
            String gate = json.has("gate") ? blockId(GsonHelper.getAsString(json, "gate")) : null;
            int margin = GsonHelper.getAsInt(json, "margin", 3);
            int gates = GsonHelper.getAsInt(json, "gates", 2);
            int width = GsonHelper.getAsInt(json, "gate_width", 3);
            if (margin < 1 || margin > 16) throw new IllegalArgumentException("'enclosure.margin' must be 1 to 16");
            if (gates < 0 || gates > 4) throw new IllegalArgumentException("'enclosure.gates' must be 0 to 4");
            if (width < 1 || width > 7) throw new IllegalArgumentException("'enclosure.gate_width' must be 1 to 7");
            ResourceLocation corner = json.has("corner") ? template(GsonHelper.getAsString(json, "corner")) : null;
            int cornerGround = GsonHelper.getAsInt(json, "corner_ground", 0);
            return new Enclosure(layers, gate, margin, gates, width, corner, cornerGround);
        }

        private static String blockId(String id) {
            ResourceLocation block = DataPackLang.parseId(id);
            if (block == null) throw new IllegalArgumentException("'" + id + "' is not a block id");
            return block.toString();
        }

        static Group group(JsonObject json) {
            String name = GsonHelper.getAsString(json, "name", "group");
            Set<ResourceLocation> templates = new java.util.LinkedHashSet<>();
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "templates")) {
                templates.add(template(GsonHelper.convertToString(element, "template")));
            }
            int min = json.has("min") ? count(json.get("min"), name + " min") : 0;
            Integer max = json.has("max") ? count(json.get("max"), name + " max") : null;
            if (max != null && min > max) throw new IllegalArgumentException("Group " + name + " has min above max");
            return new Group(name, templates, min, max);
        }

        private static ResourceLocation template(String id) {
            ResourceLocation template = DataPackLang.parseId(id);
            if (template == null) throw new IllegalArgumentException("'" + id + "' is not a template id");
            return template;
        }

        private static int count(JsonElement value, String what) {
            int count = GsonHelper.convertToInt(value, what);
            if (count < 0) throw new IllegalArgumentException(what + " must not be negative");
            return count;
        }
    }
}
