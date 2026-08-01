package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Option;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Station;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.StationType;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

    private StationCatalogs() {}

    /** Which station roles physically stand inside this worksite. */
    public static Set<StationType> stationsIn(ServerLevel level, Set<Long> extent) {
        Set<StationType> present = EnumSet.noneOf(StationType.class);
        for (long packed : extent) {
            StationType type = com.aetherianartificer.townstead.work.station.Stations
                    .stationType(level, BlockPos.of(packed));
            if (type != null) present.add(type);
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
        WorksiteStock.eachContainer(level, extent, container -> {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) continue;
                counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), Integer::sum);
            }
        });
        return counts;
    }

    /** One catalogue entry: what it makes, where, whether it can be made now, and what it needs. */
    public static Option option(DiscoveredRecipe recipe, StationType type,
                                Map<ResourceLocation, Integer> onHand) {
        return option(recipe, label(type), iconFor(type), onHand);
    }

    /**
     * The same entry named after a specific station rather than a role. A role label is right when
     * many blocks share the work ("Fire"); a produce line belongs to one block, and "Meat Grinder"
     * tells a player more than "Station" ever would.
     */
    public static Option option(DiscoveredRecipe recipe, String stationLabel,
                                ResourceLocation stationIcon, Map<ResourceLocation, Integer> onHand) {
        String missing = firstMissing(recipe, onHand);
        return Option.item(recipe.output(), stationLabel, stationIcon,
                missing == null, missing == null ? "" : missing,
                Math.max(1, recipe.outputCount()), needsOf(recipe));
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
    private static String firstMissing(DiscoveredRecipe recipe, Map<ResourceLocation, Integer> onHand) {
        for (RecipeIngredient input : recipe.inputs()) {
            boolean supplyLine = true;
            boolean satisfied = false;
            for (ResourceLocation id : input.itemIds()) {
                if (!BuiltInRegistries.ITEM.containsKey(id)) continue;
                supplyLine = false;
                if (onHand.getOrDefault(id, 0) >= input.count()) {
                    satisfied = true;
                    break;
                }
            }
            if (supplyLine || satisfied) continue;
            return "No " + itemName(input.primaryId()).toLowerCase(Locale.ROOT) + " stored here";
        }
        return null;
    }

    /**
     * What this recipe consumes, in the words a player reads. An ingredient is a set of
     * interchangeable ids, so the first registered one stands for the group.
     */
    private static List<String> needsOf(DiscoveredRecipe recipe) {
        List<String> out = new ArrayList<>(recipe.inputs().size());
        for (RecipeIngredient input : recipe.inputs()) {
            ResourceLocation named = null;
            for (ResourceLocation id : input.itemIds()) {
                if (BuiltInRegistries.ITEM.containsKey(id)) {
                    named = id;
                    break;
                }
            }
            if (named == null) continue;
            out.add(Math.max(1, input.count()) + " x " + itemName(named));
        }
        return List.copyOf(out);
    }

    private static String itemName(ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id)
                ? new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString()
                : id.getPath().replace('_', ' ');
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
        };
    }
}
