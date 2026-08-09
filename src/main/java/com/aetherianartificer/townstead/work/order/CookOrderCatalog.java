package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.order.StationCatalogs;
import com.aetherianartificer.townstead.work.order.WorksiteCatalogs;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Option;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Station;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.StationType;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.Worksites;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The cooking catalogue for a worksite: every discovered food recipe whose station actually stands
 * inside this place, and whether its ingredients are on the shelves here right now.
 *
 * <p>Filtered by the stations really present rather than by everything the mod pack could cook, so
 * a kitchen with only a campfire does not offer to bake. Offering an item a place cannot make is
 * how you get an order that sits at "waiting" forever with nothing to explain it.</p>
 *
 * <p>Everything not about <em>food in particular</em> lives in {@link StationCatalogs}, shared with
 * the brewing catalogue.</p>
 */
public final class CookOrderCatalog implements WorksiteCatalogs.Catalog {

    private CookOrderCatalog() {}

    public static void bootstrap() {
        WorksiteCatalogs.register(new CookOrderCatalog());
    }

    @Override
    public ResourceLocation taskType() {
        return com.aetherianartificer.townstead.profession.def.WorkTaskTypes.COOK;
    }

    @Override
    public List<Option> optionsFor(ServerLevel level, Worksite site) {
        // Asks the record, not the key: for a room-bound worksite the key's value is a building id,
        // so reading it as a packed position pointed the flood fill at the world origin and the
        // catalogue came back empty as soon as the cook's cached extent went stale.
        Set<Long> extent = Worksites.extentOf(level, site);
        if (extent.isEmpty()) return List.of();

        Set<StationType> present = StationCatalogs.stationsIn(level, extent);
        if (present.isEmpty()) return List.of();

        Map<ResourceLocation, Integer> onHand = StationCatalogs.stockIn(level, extent);
        // Roles alone would have one crafting bowl advertising the roaster's menu too: every
        // declared machine shares the passive_station role. A recipe that names its own station
        // is offered only where that station stands.
        Set<ResourceLocation> presentDefs = StationCatalogs.stationDefsIn(level, extent);
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        List<Option> out = new ArrayList<>();
        for (StationType type : present) {
            for (DiscoveredRecipe recipe : WorkRecipeRegistry.getFoodRecipesForStation(level, type)) {
                List<com.aetherianartificer.townstead.work.station.WorkstationDef> declared =
                        WorkRecipeRegistry.defsFor(recipe);
                com.aetherianartificer.townstead.work.station.WorkstationDef station = declared.stream()
                        .filter(def -> presentDefs.contains(def.id())).findFirst().orElse(null);
                if (!declared.isEmpty() && station == null) continue;
                // Stated, not inferred. The registry's "food" only means "not a beverage", so a
                // furnace's smelting lands in it, and no property of the recipe distinguishes a
                // baked potato from an iron ingot. What a cook may be asked for is a tag, which
                // any pack can extend — and the declaring station's own "orderable" say, so a
                // dedicated cooking station admits its whole menu in one line.
                if (!com.aetherianartificer.townstead.work.recipe.WorkOutputTags
                        .allows(com.aetherianartificer.townstead.work.recipe.WorkOutputTags.COOK,
                                recipe.output(), station == null ? null : station.orderable())) {
                    continue;
                }
                if (!seen.add(recipe.output())) continue;
                out.add(StationCatalogs.optionFrom(recipe, type, station, onHand));
            }
        }
        return out;
    }

    @Override
    public List<Station> stationsFor(ServerLevel level, Worksite site) {
        Set<Long> extent = Worksites.extentOf(level, site);
        // The roles this pack has cooking recipes for, asked rather than assumed: a role nothing
        // cooks at is not a gap in the kitchen, and now that other trades declare roles of their
        // own, listing all of them would tell a cook they are missing a grinder. Only roles whose
        // recipes are NOT tied to a named station, since those are listed one by one below.
        Set<StationType> cookRoles = EnumSet.noneOf(StationType.class);
        java.util.LinkedHashSet<com.aetherianartificer.townstead.work.station.WorkstationDef> declared =
                new java.util.LinkedHashSet<>();
        for (StationType type : StationType.values()) {
            for (DiscoveredRecipe recipe : WorkRecipeRegistry.getFoodRecipesForStation(level, type)) {
                List<com.aetherianartificer.townstead.work.station.WorkstationDef> stations =
                        WorkRecipeRegistry.defsFor(recipe);
                if (!stations.isEmpty()) declared.addAll(stations); else cookRoles.add(type);
            }
        }
        List<Station> out = new ArrayList<>(StationCatalogs.stationList(level, extent, cookRoles));
        // Named one by one, because "Station" repeated four times is not a shopping list: the
        // whole point of the strip is telling a player which block to go and build.
        out.addAll(StationCatalogs.declaredStationList(level, extent, declared));
        return out;
    }
}
