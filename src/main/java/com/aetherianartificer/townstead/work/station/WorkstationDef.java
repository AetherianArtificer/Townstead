package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.recipe.StationType;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A data-declared cooking workstation ({@code data/<ns>/workstation/*.json}): maps blocks other
 * mods add onto the cook engine's station roles, so packs extend mod support without Java.
 *
 * The jar ships Farmer's Delight's own stations this way ({@code data/townstead/workstation/}),
 * so the shipped defs double as the reference configuration — the cooking pot reads:
 *
 * <pre>
 * {
 *   "mods": "farmersdelight",
 *   "blocks": ["farmersdelight:cooking_pot"],       // ids or "#tags"
 *   "type": "hot_station",                          // hot_station | fire_station | cutting_board
 *   "container_slot": 7, "ingredient_slots": 6      // pot protocol layout (hot_station)
 * }
 * </pre>
 *
 * A def without {@code recipe_type} aliases the block onto the built-in recipe families (right
 * for FD's own stations and for blocks that extend FD's block entities). Declaring
 * {@code "recipe_type"} (with optional {@code recipe_tier}, {@code cook_time},
 * {@code beverage}) instead discovers that type's recipes generically and pairs them
 * exclusively: those recipes cook only at its stations, and its stations cook only those
 * recipes — which is why FD's shipped defs must never declare one.
 */
public record WorkstationDef(
        ResourceLocation id,
        Set<ResourceLocation> blocks,
        List<ResourceLocation> blockTags,
        StationType role,
        int containerSlot,
        int ingredientSlots,
        List<net.minecraft.core.Vec3i> stands,
        @Nullable ResourceLocation recipeType,
        int recipeTier,
        int cookTimeTicks,
        boolean beverage,
        // Protocol stations (passive_station / place_surface):
        @Nullable String adapter,
        Set<ResourceLocation> surfaceBlocks,
        List<ResourceLocation> surfaceTags,
        @Nullable ResourceLocation places,
        @Nullable ResourceLocation doneBlock,
        List<ResourceLocation> harvestTools,
        List<Produce> produces,
        FurnaceSlots furnaceSlots,
        /**
         * Whether this station's work happens as fluid inside the block, so its recipes come from
         * a compat reader rather than from generic discovery. Generic discovery would find nothing
         * useful anyway — a fermenting recipe has no item result to read.
         */
        boolean fluidStation,
        /**
         * Which registered {@link com.aetherianartificer.townstead.work.recipe.FluidRecipeSources}
         * reader supplies this station's recipes. Named rather than implied so two fluid stations
         * from different mods never read each other's recipes.
         */
        @Nullable String fluidSource,
        /**
         * Whether cooking happens on the station's top face, so a block placed above stops it
         * working. True for a campfire or a stove; false for a skillet, which holds its own item
         * and does not care what sits over it. Defaults false, which is how every station other
         * than campfires and stoves already behaved.
         */
        boolean openTop,
        /**
         * The work task whose workers fulfil this station's produce lines, so the order screen
         * knows to offer them wherever that trade works. Null means recognition only: the station
         * is walked to and driven, but nothing here is put on an order sheet — right for defs
         * whose outputs already reach the screen another way (the cook's discovered recipes) and
         * for stations nobody's task drives yet.
         */
        @Nullable ResourceLocation workTask,
        Orderable orderable,
        /**
         * Whether this station burns fuel to run. Implied for a {@code furnace_station}; stated
         * for anything else, because a machine that happens to have a fuel slot is not the same
         * as one that will not start without it. Declaring it makes fuel a real input — staged,
         * counted and shown as a need — and loads it through whichever face the block says its
         * fuel goes in.
         */
        boolean needsFuel,
        /**
         * Blocks (ids or {@code #tags}) that must sit DIRECTLY BELOW for this station to work —
         * a cooking pot needs a fire under it. Empty means the station stands on its own.
         *
         * <p>An unsupported station is not a station <em>right now</em>, the same way a covered
         * open-top one is not: a villager will not walk to it and the catalogue reports it
         * missing, which is the honest answer. Without this a cook loads a cold pot and waits
         * on a recipe that can never finish, with nothing on screen saying why.</p>
         */
        Set<ResourceLocation> supportBelow,
        List<ResourceLocation> supportBelowTags,
        ShiftEndPolicy shiftEnd) {

    /**
     * Which of this station's outputs a trade may be ordered for. The trade's output tag stays
     * the base rule; this is the station author speaking at the right altitude — a dedicated
     * cooking station declares {@code "orderable": "all"} once instead of tagging every dish,
     * while a general-purpose machine (a mincer that also grinds ore) stays {@code "tagged"}
     * and names its few food outputs in {@code allow}. {@code block} removes an output even
     * when tagged or allowed. Entries are item ids or {@code "#tags"}; evaluation lives in
     * {@link com.aetherianartificer.townstead.work.recipe.WorkOutputTags}.
     */
    public record Orderable(boolean all, List<String> allow, List<String> block) {
        public static final Orderable TAGGED = new Orderable(false, List.of(), List.of());
        public static final Orderable ALL = new Orderable(true, List.of(), List.of());
    }

    /**
     * Which slots a {@code furnace_station} loads and unloads. Defaults are vanilla's, which every
     * block entity extending {@code AbstractFurnaceBlockEntity} inherits, so a modded furnace that
     * does the usual thing needs no slot lines at all.
     */
    public record FurnaceSlots(int input, int fuel, int output) {
        public static final FurnaceSlots VANILLA = new FurnaceSlots(0, 1, 2);
    }

    /**
     * One synthetic production line of a protocol station: consume {@code inputs} (ids or
     * {@code #tags}), optionally garnish with up to {@code extrasMax} DISTINCT items from
     * {@code extrasTag} (never required — a plainer product still ships), wait {@code
     * timeTicks}, yield {@code output}.
     *
     * <p>{@code copies} names an input whose consumed stack the output is a
     * component-preserving copy of — a duplicated map is THAT map, a copied book THAT book.
     * Minting by output id would hand back a blank one, which is the components trap on the
     * production side.</p>
     */
    public record Produce(List<String> inputs, @Nullable ResourceLocation extrasTag, int extrasMax,
                          ResourceLocation output, int outputCount, int timeTicks,
                          @Nullable ResourceLocation copies) {}

    public WorkstationDef(ResourceLocation id, Set<ResourceLocation> blocks, List<ResourceLocation> blockTags,
                          StationType role, int containerSlot, int ingredientSlots,
                          List<net.minecraft.core.Vec3i> stands, @Nullable ResourceLocation recipeType,
                          int recipeTier, int cookTimeTicks, boolean beverage) {
        this(id, blocks, blockTags, role, containerSlot, ingredientSlots, stands, recipeType,
                recipeTier, cookTimeTicks, beverage,
                null, Set.of(), List.of(), null, null, List.of(), List.of(), FurnaceSlots.VANILLA,
                false, null, false, null, Orderable.TAGGED, false, Set.of(), List.of(),
                ShiftEndPolicy.FINISH);
    }

    static @Nullable WorkstationDef parse(ResourceLocation id, JsonObject obj) {
        StationType role = switch (GsonHelper.getAsString(obj, "type", "")) {
            case "hot_station" -> StationType.HOT_STATION;
            case "fire_station" -> StationType.FIRE_STATION;
            case "cutting_board" -> StationType.CUTTING_BOARD;
            case "passive_station" -> StationType.PASSIVE_STATION;
            case "place_surface" -> StationType.PLACE_SURFACE;
            case "furnace_station" -> StationType.FURNACE_STATION;
            case "craft_surface" -> StationType.CRAFT_SURFACE;
            default -> null;
        };
        if (role == null) return null;

        Set<ResourceLocation> blocks = new LinkedHashSet<>();
        List<ResourceLocation> tags = new ArrayList<>();
        if (obj.has("block") && obj.get("block").isJsonPrimitive()) {
            if (!readEntry(obj.get("block").getAsString(), blocks, tags)) return null;
        }
        if (obj.has("blocks") && obj.get("blocks").isJsonArray()) {
            for (JsonElement e : obj.getAsJsonArray("blocks")) {
                if (!e.isJsonPrimitive() || !readEntry(e.getAsString(), blocks, tags)) return null;
            }
        }
        if (blocks.isEmpty() && tags.isEmpty()) return null;

        // Authored stand cells ("pathfinding help"): offsets from the station block where a
        // villager should stand to work it. Preferred over generic adjacent-cell guesses.
        List<net.minecraft.core.Vec3i> stands = new ArrayList<>();
        if (obj.has("stands") && obj.get("stands").isJsonArray()) {
            for (JsonElement e : obj.getAsJsonArray("stands")) {
                if (!e.isJsonArray() || e.getAsJsonArray().size() != 3) return null;
                var offset = e.getAsJsonArray();
                for (JsonElement axis : offset) {
                    if (!axis.isJsonPrimitive() || !axis.getAsJsonPrimitive().isNumber()) return null;
                }
                stands.add(new net.minecraft.core.Vec3i(
                        offset.get(0).getAsInt(), offset.get(1).getAsInt(), offset.get(2).getAsInt()));
            }
        }

        ResourceLocation recipeType = obj.has("recipe_type")
                ? ResourceLocation.tryParse(GsonHelper.getAsString(obj, "recipe_type", "")) : null;
        if (obj.has("recipe_type") && recipeType == null) return null;

        // Protocol fields
        String adapter = obj.has("adapter") ? GsonHelper.getAsString(obj, "adapter", "") : null;
        Set<ResourceLocation> surfaceBlocks = new LinkedHashSet<>();
        List<ResourceLocation> surfaceTags = new ArrayList<>();
        if (obj.has("surface") && obj.get("surface").isJsonArray()) {
            for (JsonElement e : obj.getAsJsonArray("surface")) {
                if (!e.isJsonPrimitive() || !readEntry(e.getAsString(), surfaceBlocks, surfaceTags)) return null;
            }
        } else if (obj.has("surface")) {
            if (!readEntry(GsonHelper.getAsString(obj, "surface", ""), surfaceBlocks, surfaceTags)) return null;
        }
        ResourceLocation places = obj.has("places")
                ? ResourceLocation.tryParse(GsonHelper.getAsString(obj, "places", "")) : null;
        ResourceLocation doneBlock = obj.has("done")
                ? ResourceLocation.tryParse(GsonHelper.getAsString(obj, "done", "")) : null;
        List<ResourceLocation> harvestTools = new ArrayList<>();
        if (obj.has("tool")) {
            JsonElement tool = obj.get("tool");
            if (tool.isJsonArray()) {
                for (JsonElement e : tool.getAsJsonArray()) {
                    ResourceLocation toolId = ResourceLocation.tryParse(e.getAsString());
                    if (toolId == null) return null;
                    harvestTools.add(toolId);
                }
            } else {
                ResourceLocation toolId = ResourceLocation.tryParse(tool.getAsString());
                if (toolId == null) return null;
                harvestTools.add(toolId);
            }
        }
        List<Produce> produces = new ArrayList<>();
        if (obj.has("produces") && obj.get("produces").isJsonArray()) {
            for (JsonElement e : obj.getAsJsonArray("produces")) {
                if (!e.isJsonObject()) return null;
                JsonObject p = e.getAsJsonObject();
                List<String> inputs = new ArrayList<>();
                for (JsonElement in : GsonHelper.getAsJsonArray(p, "inputs", new com.google.gson.JsonArray())) {
                    if (!in.isJsonPrimitive()) return null;
                    inputs.add(in.getAsString());
                }
                ResourceLocation output = ResourceLocation.tryParse(GsonHelper.getAsString(p, "output", ""));
                if (output == null || inputs.isEmpty()) return null;
                ResourceLocation extrasTag = null;
                if (p.has("extras")) {
                    String raw = GsonHelper.getAsString(p, "extras", "");
                    extrasTag = ResourceLocation.tryParse(raw.startsWith("#") ? raw.substring(1) : raw);
                    if (extrasTag == null) return null;
                }
                ResourceLocation copies = null;
                if (p.has("copies")) {
                    copies = ResourceLocation.tryParse(GsonHelper.getAsString(p, "copies", ""));
                    if (copies == null) return null;
                }
                produces.add(new Produce(List.copyOf(inputs), extrasTag,
                        GsonHelper.getAsInt(p, "extras_max", 0),
                        output,
                        GsonHelper.getAsInt(p, "count", 1),
                        GsonHelper.getAsInt(p, "time", 200),
                        copies));
            }
        }
        // A protocol station has to say what comes out of it, one way or the other: either inline
        // production lines, or a recipe type to discover them from. A place-surface station only
        // has the inline form, since its output is a block it turns into.
        // Naming a reader is itself the declaration that this station's work happens as fluid.
        String fluidSource = obj.has("fluid_source") ? GsonHelper.getAsString(obj, "fluid_source", "") : null;
        if (fluidSource != null && fluidSource.isBlank()) return null;
        boolean fluidStation = GsonHelper.getAsBoolean(obj, "fluid_station", false) || fluidSource != null;
        boolean statesItsOutput = !produces.isEmpty()
                || ((role == StationType.PASSIVE_STATION || role == StationType.CRAFT_SURFACE)
                        && (recipeType != null || fluidStation));
        if ((role == StationType.PASSIVE_STATION || role == StationType.PLACE_SURFACE
                || role == StationType.CRAFT_SURFACE) && !statesItsOutput) {
            return null;
        }

        FurnaceSlots furnaceSlots = new FurnaceSlots(
                GsonHelper.getAsInt(obj, "input_slot", FurnaceSlots.VANILLA.input()),
                GsonHelper.getAsInt(obj, "fuel_slot", FurnaceSlots.VANILLA.fuel()),
                GsonHelper.getAsInt(obj, "output_slot", FurnaceSlots.VANILLA.output()));
        // A furnace's outputs come from its recipe type, not from declared produce lines, so it is
        // the one role that cannot work without one.
        if (role == StationType.FURNACE_STATION && recipeType == null) return null;

        ResourceLocation workTask = obj.has("work_task")
                ? ResourceLocation.tryParse(GsonHelper.getAsString(obj, "work_task", "")) : null;
        if (obj.has("work_task") && workTask == null) return null;

        ShiftEndPolicy shiftEnd = ShiftEndPolicy.FINISH;
        if (obj.has("shift_end")) {
            JsonElement value = obj.get("shift_end");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
            shiftEnd = ShiftEndPolicy.parse(value.getAsString());
            if (shiftEnd == null) return null;
        }

        Orderable orderable = parseOrderable(obj.get("orderable"));
        if (orderable == null) return null;

        Set<ResourceLocation> supportBelow = new LinkedHashSet<>();
        List<ResourceLocation> supportBelowTags = new ArrayList<>();
        if (obj.has("support_below")) {
            JsonElement support = obj.get("support_below");
            if (support.isJsonArray()) {
                for (JsonElement e : support.getAsJsonArray()) {
                    if (!e.isJsonPrimitive()
                            || !readEntry(e.getAsString(), supportBelow, supportBelowTags)) return null;
                }
            } else if (!support.isJsonPrimitive()
                    || !readEntry(support.getAsString(), supportBelow, supportBelowTags)) {
                return null;
            }
            if (supportBelow.isEmpty() && supportBelowTags.isEmpty()) return null;
        }

        return new WorkstationDef(id, Set.copyOf(blocks), List.copyOf(tags), role,
                GsonHelper.getAsInt(obj, "container_slot", 7),
                GsonHelper.getAsInt(obj, "ingredient_slots", 6),
                List.copyOf(stands),
                recipeType,
                GsonHelper.getAsInt(obj, "recipe_tier", 0),
                GsonHelper.getAsInt(obj, "cook_time", 200),
                GsonHelper.getAsBoolean(obj, "beverage", false),
                adapter,
                Set.copyOf(surfaceBlocks), List.copyOf(surfaceTags),
                places, doneBlock, List.copyOf(harvestTools), List.copyOf(produces), furnaceSlots,
                fluidStation, fluidSource,
                GsonHelper.getAsBoolean(obj, "open_top", false),
                workTask,
                orderable,
                GsonHelper.getAsBoolean(obj, "fuel", false),
                Set.copyOf(supportBelow), List.copyOf(supportBelowTags), shiftEnd);
    }

    /**
     * {@code "orderable"}: absent or {@code "tagged"} → tag-gated (the default); {@code "all"} →
     * every output; an object with optional {@code "mode": "all"}, {@code allow} and {@code block}
     * arrays for the mixed cases. Null means the field was present but malformed, refusing the def.
     */
    private static @Nullable Orderable parseOrderable(@Nullable JsonElement element) {
        if (element == null) return Orderable.TAGGED;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return switch (element.getAsString()) {
                case "all" -> new Orderable(true, List.of(), List.of());
                case "tagged" -> Orderable.TAGGED;
                default -> null;
            };
        }
        if (!element.isJsonObject()) return null;
        JsonObject obj = element.getAsJsonObject();
        String mode = GsonHelper.getAsString(obj, "mode", "tagged");
        if (!"all".equals(mode) && !"tagged".equals(mode)) return null;
        List<String> allow = readIdList(obj, "allow");
        List<String> block = readIdList(obj, "block");
        if (allow == null || block == null) return null;
        return new Orderable("all".equals(mode), allow, block);
    }

    private static @Nullable List<String> readIdList(JsonObject obj, String key) {
        if (!obj.has(key)) return List.of();
        if (!obj.get(key).isJsonArray()) return null;
        List<String> out = new ArrayList<>();
        for (JsonElement e : obj.getAsJsonArray(key)) {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) return null;
            String raw = e.getAsString();
            if (ResourceLocation.tryParse(raw.startsWith("#") ? raw.substring(1) : raw) == null) return null;
            out.add(raw);
        }
        return List.copyOf(out);
    }

    private static boolean readEntry(String raw, Set<ResourceLocation> blocks, List<ResourceLocation> tags) {
        if (raw.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(raw.substring(1));
            if (tagId == null) return false;
            tags.add(tagId);
        } else {
            ResourceLocation blockId = ResourceLocation.tryParse(raw);
            if (blockId == null) return false;
            blocks.add(blockId);
        }
        return true;
    }
}
