package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Need;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Option;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Station;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.RequirementLabels;
import com.aetherianartificer.townstead.work.recipe.StationType;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.station.WorkstationDef;
import com.aetherianartificer.townstead.work.station.Workstations;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The parts of "what can this place make" that are true of every station-driven trade.
 *
 * <p>Extracted the moment a second trade needed them, and moved out of the cook's compat package
 * the moment a third did. Cooking, brewing and grinding differ only in which recipe list they read; the extent walk, the station survey, the one-sweep stock count and the
 * blocker wording are the same question asked at a different counter, and a third trade should
 * cost a dozen lines rather than a copy of a file.</p>
 *
 * <p>Station matching is by <em>role</em>, never by block: any block a pack maps onto
 * {@code fire_station} inherits every fire-station recipe without anyone writing compat for it.</p>
 */
public final class StationCatalogs {

    private record NeedKey(List<ResourceLocation> items, String label) {
        private NeedKey {
            items = List.copyOf(items);
            label = label == null ? "" : label;
        }
    }

    private StationCatalogs() {}

    /**
     * Which station roles are physically installed inside this worksite.
     *
     * <p>The order sheet asks what the room is equipped to make, not whether each machine can
     * run on this exact tick. A cold stove, an empty fuel slot, or another temporarily unmet
     * runtime requirement must not erase the machine's recipes from the sheet. Execution uses
     * {@link com.aetherianartificer.townstead.work.station.Stations#stationType(ServerLevel,
     * BlockPos)} and still enforces those requirements before work starts.</p>
     */
    public static Set<StationType> stationsIn(ServerLevel level, Set<Long> extent) {
        Set<StationType> present = EnumSet.noneOf(StationType.class);
        for (long packed : extent) {
            BlockPos pos = BlockPos.of(packed);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            StationType type = com.aetherianartificer.townstead.work.station.Stations
                    .stationType(state);
            // A place-surface station's anchor is intentionally air. Preserve that one physical
            // form without asking operational requirements of ordinary station blocks.
            if (type == null && state.isAir()
                    && com.aetherianartificer.townstead.work.station.StationProtocols
                    .surfaceDefBelow(level, pos) != null) {
                type = StationType.PLACE_SURFACE;
            }
            if (type != null) present.add(type);
        }
        return present;
    }

    /**
     * Which DECLARED workstations stand inside this worksite, by def id.
     *
     * <p>Roles are too coarse to answer "can this place make that". Every data-declared machine
     * is a {@code passive_station}, so owning one crafting bowl would otherwise advertise the
     * roaster's whole menu as well. The work engine already pairs a declared recipe type to its
     * own stations exclusively; this is the catalogue learning the same rule, so the sheet only
     * offers what the room can actually cook.</p>
     */
    public static Set<ResourceLocation> stationDefsIn(ServerLevel level, Set<Long> extent) {
        Set<ResourceLocation> present = new java.util.HashSet<>();
        for (long packed : extent) {
            BlockPos pos = BlockPos.of(packed);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            WorkstationDef def = com.aetherianartificer.townstead.work.station.Workstations
                    .byState(state);
            if (def == null && state.isAir()) {
                def = com.aetherianartificer.townstead.work.station.StationProtocols
                        .surfaceDefBelow(level, pos);
            }
            if (def != null) present.add(def.id());
        }
        return present;
    }

    /**
     * Every item in every container inside the worksite, counted once.
     *
     * <p>One sweep, reused for every recipe. Checking each recipe against the world separately
     * would be a few hundred walks of the same chests per screen open.</p>
     */
    public static Map<ResourceLocation, Integer> stockIn(ServerLevel level, Set<Long> extent) {
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        WorksiteStock.eachStack(level, extent, stack -> {
            if (stack.isEmpty()) return;
            counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), Integer::sum);
        });
        return counts;
    }

    /**
     * Catalogue view of a worksite's stock, including its assigned workers' live inventories.
     * This is deliberately separate from the execution view: another cook's pockets satisfy the
     * worksite's order total, but are not an ingredient source the acting cook may reach into.
     */
    public static Map<ResourceLocation, Integer> stockIn(ServerLevel level, Worksite site,
                                                          Set<Long> extent) {
        Map<ResourceLocation, Integer> counts = stockIn(level, extent);
        WorksiteStock.eachAssociatedStack(level, site, stack -> {
            if (stack.isEmpty()) return;
            counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), Integer::sum);
        });
        return counts;
    }

    /** One catalogue entry: what it makes, where, whether it can be made now, and what it needs. */
    public static Option option(DiscoveredRecipe recipe, StationType type,
                                Map<ResourceLocation, Integer> onHand) {
        return option(recipe, label(type), iconFor(type), onHand);
    }

    /**
     * The same, named after the station that declared the recipe when one did.
     *
     * <p>A role label is right when many blocks share the work ("Fire" covers a campfire and a
     * skillet alike). It is useless when the role is a catch-all: every data-declared machine is
     * a {@code passive_station}, so a roaster, a stove and a mincer all read "Station" and the
     * player cannot tell which block to go build. A declared station names itself.</p>
     */
    public static Option optionFrom(DiscoveredRecipe recipe, StationType type,
                                    @Nullable WorkstationDef def,
                                    Map<ResourceLocation, Integer> onHand) {
        if (def == null) return option(recipe, type, onHand);
        ResourceLocation block = firstRegisteredBlock(def);
        if (block == null) return option(recipe, type, onHand);
        Option option = option(recipe, itemName(block), block, onHand);
        var v2 = com.aetherianartificer.townstead.work.station.Workstations.v2ByBlockId(block);
        return option.withOperated(v2 != null && v2.hasReservation());
    }

    /** The def's first block that actually exists, for naming and for its sprite. */
    private static @Nullable ResourceLocation firstRegisteredBlock(WorkstationDef def) {
        for (ResourceLocation block : def.blocks()) {
            if (BuiltInRegistries.ITEM.containsKey(block)) return block;
        }
        return null;
    }

    /**
     * The same entry named after a specific station rather than a role. A role label is right when
     * many blocks share the work ("Fire"); a produce line belongs to one block, and "Meat Grinder"
     * tells a player more than "Station" ever would.
     */
    public static Option option(DiscoveredRecipe recipe, String stationLabel,
                                ResourceLocation stationIcon, Map<ResourceLocation, Integer> onHand) {
        List<Need> needs = needsOf(recipe);
        List<Need> missing = missingOf(recipe, onHand);
        String blocker = describeMissing(missing);
        return Option.item(recipe.output(), stationLabel, stationIcon,
                missing.isEmpty(), blocker,
                Math.max(1, recipe.outputCount()), needs, missing);
    }

    /**
     * One entry per declared station, named after its own block and lit when the worksite has
     * one. The missing ones are the point: someone looking at a kitchen that cannot roast wants
     * to be told "Roaster", not left wondering where the roasts went.
     */
    public static List<Station> declaredStationList(ServerLevel level, Set<Long> extent,
                                                    java.util.Collection<WorkstationDef> defs) {
        if (defs.isEmpty()) return List.of();
        Set<ResourceLocation> present = stationDefsIn(level, extent);
        Set<String> seen = new java.util.LinkedHashSet<>();
        List<Station> out = new ArrayList<>();
        for (WorkstationDef def : defs) {
            ResourceLocation block = firstRegisteredBlock(def);
            if (block == null) continue;
            String label = itemName(block);
            if (!seen.add(label)) continue;
            out.add(new Station(label, block, present.contains(def.id())));
        }
        return out;
    }

    /** Every station role a pack provides a block for, and whether this worksite has one. */
    public static List<Station> stationList(ServerLevel level, Set<Long> extent) {
        return stationList(level, extent, EnumSet.allOf(StationType.class));
    }

    /**
     * The same, narrowed to the roles one catalogue actually speaks for.
     *
     * <p>A strip listing every role in the game would tell a kitchen it is missing a meat grinder.
     * The roles a trade has no recipes for are not gaps in that trade's workshop.</p>
     */
    public static List<Station> stationList(ServerLevel level, Set<Long> extent,
                                            Set<StationType> roles) {
        Set<StationType> present = stationsIn(level, extent);
        List<Station> out = new ArrayList<>();
        for (StationType type : StationType.values()) {
            if (!roles.contains(type)) continue;
            ResourceLocation icon = iconFor(type);
            // A role nothing in this pack provides a block for is not a gap in the kitchen, it is
            // a role that does not exist here. Leave it out entirely.
            if (NO_ICON.equals(icon)) continue;
            out.add(new Station(label(type), icon, present.contains(type)));
        }
        return out;
    }

    /**
     * The name of the first input this place cannot supply, or null when it can supply them all.
     *
     * <p>An ingredient is a set of interchangeable ids, so it is satisfied when any one of them is
     * present in quantity. Ids with no item behind them are supply lines rather than things on a
     * shelf, and are not judged here — a missing bucket of water is not a missing onion.</p>
     */
    private static List<Need> missingOf(DiscoveredRecipe recipe,
                                        Map<ResourceLocation, Integer> onHand) {
        Map<NeedKey, Integer> missing = new java.util.LinkedHashMap<>();
        if (recipe.requiresTool()) {
            List<ResourceLocation> tools = registered(WorkRecipeRegistry.recipeToolIds(recipe));
            if (!tools.isEmpty() && tools.stream().noneMatch(id -> onHand.getOrDefault(id, 0) > 0)) {
                missing.merge(new NeedKey(tools,
                        WorkRecipeRegistry.recipeToolRequirementName(recipe)), 1, Integer::sum);
            }
        }

        Map<ResourceLocation, Integer> claimed = new HashMap<>();
        for (RecipeIngredient input : recipe.inputs()) {
            List<ResourceLocation> alternatives = registered(input.itemIds());
            ResourceLocation best = null;
            int bestAvailable = 0;
            for (ResourceLocation id : alternatives) {
                int available = Math.max(0,
                        onHand.getOrDefault(id, 0) - claimed.getOrDefault(id, 0));
                if (best == null || available > bestAvailable) {
                    best = id;
                    bestAvailable = available;
                }
                if (available >= Math.max(1, input.count())) {
                    claimed.merge(id, Math.max(1, input.count()), Integer::sum);
                    best = null;
                    break;
                }
            }
            // An unresolved id is a synthetic supply line. It has no single icon and retains the
            // existing runtime-only availability check rather than pretending it is an item.
            if (best == null) continue;
            int needed = Math.max(1, input.count());
            int used = Math.min(needed, bestAvailable);
            if (used > 0) claimed.merge(best, used, Integer::sum);
            int shortfall = needed - used;
            if (shortfall > 0) missing.merge(new NeedKey(alternatives,
                            input.sourceTag() == null ? ""
                                    : RequirementLabels.tagName(input.sourceTag())),
                    shortfall, Integer::sum);
        }

        ResourceLocation vessel = recipe.containerItemId();
        if (vessel != null && BuiltInRegistries.ITEM.containsKey(vessel)) {
            int needed = Math.max(1, recipe.containerCount());
            int available = Math.max(0,
                    onHand.getOrDefault(vessel, 0) - claimed.getOrDefault(vessel, 0));
            int used = Math.min(needed, available);
            if (used > 0) claimed.merge(vessel, used, Integer::sum);
            if (used < needed) missing.merge(new NeedKey(List.of(vessel), ""),
                    needed - used, Integer::sum);
        }

        List<Need> out = new ArrayList<>(missing.size());
        missing.forEach((key, count) -> out.add(new Need(key.items(), count, key.label())));
        return List.copyOf(out);
    }

    /** Compact row/catalogue wording; the structured list remains available to Details. */
    public static String describeMissing(List<Need> missing) {
        if (missing == null || missing.isEmpty()) return "";
        StringBuilder out = new StringBuilder("Missing: ");
        int shown = Math.min(2, missing.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) out.append(", ");
            Need need = missing.get(i);
            if (need.count() > 1) out.append(need.count()).append(' ');
            out.append(needName(need));
        }
        if (missing.size() > shown) out.append(" +").append(missing.size() - shown).append(" more");
        return out.toString();
    }

    /**
     * What this recipe consumes, one line per distinct thing. An ingredient is a set of
     * interchangeable ids, so the first registered one stands for the group — and groups that
     * resolve to the same thing merge, because a recipe listing the same bean four times is
     * asking for four beans, not asking four times.
     */
    private static List<Need> needsOf(DiscoveredRecipe recipe) {
        Map<NeedKey, Integer> counts = new java.util.LinkedHashMap<>();
        for (RecipeIngredient input : recipe.inputs()) {
            List<ResourceLocation> alternatives = registered(input.itemIds());
            // A group with no item behind it can still be a real need: a supply line (furnace
            // fuel) is fetched off the shelves like anything else, so hiding it read one thing
            // short of what the work takes. The line id rides the packet; the screen owns how
            // a line is drawn.
            if (alternatives.isEmpty() && com.aetherianartificer.townstead.supply.SupplyLines
                    .isLineId(input.primaryId())) {
                alternatives = List.of(input.primaryId());
            }
            if (!alternatives.isEmpty()) counts.merge(new NeedKey(alternatives,
                            input.sourceTag() == null ? ""
                                    : RequirementLabels.tagName(input.sourceTag())),
                    Math.max(1, input.count()), Integer::sum);
        }
        // The vessel the result leaves in is fetched off the shelves like anything else, so a
        // stew that never mentioned bowls was reading one thing short of what it takes.
        ResourceLocation vessel = recipe.containerItemId();
        if (vessel != null && BuiltInRegistries.ITEM.containsKey(vessel)) {
            counts.merge(new NeedKey(List.of(vessel), ""),
                    Math.max(1, recipe.containerCount()), Integer::sum);
        }
        // A tool is reusable rather than consumed, but it is still something the worksite needs
        // before this recipe can run. Showing it here keeps the order sheet and worker planner in
        // agreement about whether the kitchen is actually equipped.
        List<ResourceLocation> tools = WorkRecipeRegistry.recipeToolIds(recipe);
        tools = registered(tools);
        if (!tools.isEmpty()) counts.merge(new NeedKey(tools,
                WorkRecipeRegistry.recipeToolRequirementName(recipe)), 1, Math::max);
        List<Need> out = new ArrayList<>(counts.size());
        counts.forEach((key, count) -> out.add(new Need(key.items(), count, key.label())));
        return List.copyOf(out);
    }

    private static List<ResourceLocation> registered(List<ResourceLocation> ids) {
        return ids.stream().filter(BuiltInRegistries.ITEM::containsKey).distinct().toList();
    }

    /** A neutral group name for compact blocker prose; details rotate the concrete choices. */
    private static String needName(Need need) {
        if (!need.label().isBlank()) return need.label();
        if (need.items().isEmpty()) return "item";
        if (need.items().size() == 1) return itemName(need.item());
        List<String[]> names = need.items().stream().map(StationCatalogs::itemName)
                .map(name -> name.split(" ")).toList();
        List<String> suffix = new ArrayList<>();
        for (int offset = 1; ; offset++) {
            final int at = offset;
            if (names.stream().anyMatch(parts -> parts.length < at)) break;
            String word = names.get(0)[names.get(0).length - offset];
            if (names.stream().anyMatch(parts -> !word.equals(parts[parts.length - at]))) break;
            suffix.add(0, word);
        }
        if (!suffix.isEmpty()) return String.join(" ", suffix);
        // Never hide the actual choices behind "matching item". The client animates tagged
        // alternatives in queued rows; this server-side text remains useful to logs and clients
        // that do not yet have that presentation path.
        int shown = Math.min(2, need.items().size());
        String concrete = need.items().subList(0, shown).stream()
                .map(StationCatalogs::itemName).collect(java.util.stream.Collectors.joining(" or "));
        return need.items().size() > shown ? concrete + " or another option" : concrete;
    }

    private static String itemName(ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id)
                ? new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString()
                : id.getPath().replace('_', ' ');
    }

    /** An item's display name for catalogue labels ("Copy Filled Map"). */
    public static String itemNameOf(ResourceLocation id) {
        return itemName(id);
    }

    private static final ResourceLocation NO_ICON = BuiltInRegistries.ITEM.getKey(Items.AIR);

    /**
     * A block that plays this role, so the screen can show the thing rather than the word. Falls
     * back to air, which the screen renders as nothing rather than as a missing-texture cube.
     */
    public static ResourceLocation iconFor(StationType type) {
        for (WorkstationDef def : Workstations.all()) {
            if (def.role() != type) continue;
            for (ResourceLocation block : def.blocks()) {
                if (BuiltInRegistries.ITEM.containsKey(block)) return block;
            }
        }
        return NO_ICON;
    }

    public static String label(StationType type) {
        return switch (type) {
            case HOT_STATION -> "Cooking pot";
            case FIRE_STATION -> "Fire";
            case CUTTING_BOARD -> "Cutting board";
            case PASSIVE_STATION -> "Station";
            case PLACE_SURFACE -> "Surface";
            case FURNACE_STATION -> "Furnace";
            case CRAFT_SURFACE -> "Workbench";
        };
    }
}
