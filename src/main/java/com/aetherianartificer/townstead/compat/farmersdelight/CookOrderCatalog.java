package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry;
import com.aetherianartificer.townstead.work.order.WorksiteCatalogs;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Option;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Station;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.StationType;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.Worksites;
import com.aetherianartificer.townstead.work.station.WorkstationDef;
import com.aetherianartificer.townstead.work.station.Workstations;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The cooking catalogue for a worksite: every discovered recipe whose station actually stands
 * inside this place, and whether its ingredients are on the shelves here right now.
 *
 * <p>Filtered by the stations really present rather than by everything the mod pack could cook, so
 * a kitchen with only a campfire does not offer to bake. Offering an item a place cannot make is
 * how you get an order that sits at "waiting" forever with nothing to explain it.</p>
 *
 * <p>Station matching is by <em>role</em>, never by block: any block a pack maps onto
 * {@code fire_station} inherits every fire-station recipe without anyone writing compat for it.</p>
 */
public final class CookOrderCatalog implements WorksiteCatalogs.Catalog {

    private CookOrderCatalog() {}

    public static void bootstrap() {
        WorksiteCatalogs.register(new CookOrderCatalog());
    }

    @Override
    public List<Option> optionsFor(ServerLevel level, Worksite site) {
        // Asks the record, not the key: for a room-bound worksite the key's value is a building id,
        // so reading it as a packed position pointed the flood fill at the world origin and the
        // catalogue came back empty as soon as the cook's cached extent went stale.
        Set<Long> extent = Worksites.extentOf(level, site);
        if (extent.isEmpty()) return List.of();

        Set<StationType> present = stationsIn(level, extent);
        if (present.isEmpty()) return List.of();

        // One sweep of the worksite's containers, reused for every recipe. Checking each recipe
        // against the world separately would be a few hundred flood fills per screen open.
        Map<ResourceLocation, Integer> onHand = stockIn(level, extent);

        Set<ResourceLocation> seen = new LinkedHashSet<>();
        List<Option> out = new ArrayList<>();
        for (StationType type : present) {
            for (DiscoveredRecipe recipe : ModRecipeRegistry.getRecipesForStation(level, type)) {
                if (!seen.add(recipe.output())) continue;
                String missing = firstMissing(recipe, onHand);
                out.add(new Option(recipe.output(), label(type), iconFor(type),
                        missing == null, missing == null ? "" : missing));
            }
        }
        return out;
    }

    @Override
    public List<Station> stationsFor(ServerLevel level, Worksite site) {
        Set<StationType> present = stationsIn(level, Worksites.extentOf(level, site));
        List<Station> out = new ArrayList<>();
        for (StationType type : StationType.values()) {
            ResourceLocation icon = iconFor(type);
            // A role nothing in this pack provides a block for is not a gap in the kitchen, it is
            // a role that does not exist here. Leave it out entirely.
            if (NO_ICON.equals(icon)) continue;
            out.add(new Station(label(type), icon, present.contains(type)));
        }
        return out;
    }

    // ── The world ──

    private static Set<StationType> stationsIn(ServerLevel level, Set<Long> extent) {
        Set<StationType> present = EnumSet.noneOf(StationType.class);
        for (long packed : extent) {
            StationType type = com.aetherianartificer.townstead.work.station.Stations
                    .stationType(level, BlockPos.of(packed));
            if (type != null) present.add(type);
        }
        return present;
    }

    /** Every item in every container inside the worksite, counted once. */
    private static Map<ResourceLocation, Integer> stockIn(ServerLevel level, Set<Long> extent) {
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (long packed : extent) {
            BlockEntity blockEntity = level.getBlockEntity(BlockPos.of(packed));
            if (!(blockEntity instanceof Container container)) continue;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) continue;
                counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), Integer::sum);
            }
        }
        return counts;
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
            return "No " + itemName(input.primaryId()) + " stored here";
        }
        return null;
    }

    private static String itemName(ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id)
                ? new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString().toLowerCase(java.util.Locale.ROOT)
                : id.getPath().replace('_', ' ');
    }

    // ── Naming ──

    /**
     * A block that plays this role, so the screen can show the thing rather than the word. Falls
     * back to air, which the screen renders as nothing rather than as a missing-texture cube.
     */
    private static ResourceLocation iconFor(StationType type) {
        for (WorkstationDef def : Workstations.all()) {
            if (def.role() != type) continue;
            for (ResourceLocation block : def.blocks()) {
                if (BuiltInRegistries.ITEM.containsKey(block)) return block;
            }
        }
        return NO_ICON;
    }

    private static final ResourceLocation NO_ICON = BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.AIR);

    private static String label(StationType type) {
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
