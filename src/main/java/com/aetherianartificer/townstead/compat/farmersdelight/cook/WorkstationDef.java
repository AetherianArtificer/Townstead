package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.StationType;
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
        List<Produce> produces) {

    /**
     * One synthetic production line of a protocol station: consume {@code inputs} (ids or
     * {@code #tags}), optionally garnish with up to {@code extrasMax} DISTINCT items from
     * {@code extrasTag} (never required — a plainer product still ships), wait {@code
     * timeTicks}, yield {@code output}.
     */
    public record Produce(List<String> inputs, @Nullable ResourceLocation extrasTag, int extrasMax,
                          ResourceLocation output, int outputCount, int timeTicks) {}

    public WorkstationDef(ResourceLocation id, Set<ResourceLocation> blocks, List<ResourceLocation> blockTags,
                          StationType role, int containerSlot, int ingredientSlots,
                          List<net.minecraft.core.Vec3i> stands, @Nullable ResourceLocation recipeType,
                          int recipeTier, int cookTimeTicks, boolean beverage) {
        this(id, blocks, blockTags, role, containerSlot, ingredientSlots, stands, recipeType,
                recipeTier, cookTimeTicks, beverage,
                null, Set.of(), List.of(), null, null, List.of(), List.of());
    }

    static @Nullable WorkstationDef parse(ResourceLocation id, JsonObject obj) {
        StationType role = switch (GsonHelper.getAsString(obj, "type", "")) {
            case "hot_station" -> StationType.HOT_STATION;
            case "fire_station" -> StationType.FIRE_STATION;
            case "cutting_board" -> StationType.CUTTING_BOARD;
            case "passive_station" -> StationType.PASSIVE_STATION;
            case "place_surface" -> StationType.PLACE_SURFACE;
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
                produces.add(new Produce(List.copyOf(inputs), extrasTag,
                        GsonHelper.getAsInt(p, "extras_max", 0),
                        output,
                        GsonHelper.getAsInt(p, "count", 1),
                        GsonHelper.getAsInt(p, "time", 200)));
            }
        }
        // Protocol roles must declare what they produce; without it the station can do nothing.
        if ((role == StationType.PASSIVE_STATION || role == StationType.PLACE_SURFACE) && produces.isEmpty()) {
            return null;
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
                places, doneBlock, List.copyOf(harvestTools), List.copyOf(produces));
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
