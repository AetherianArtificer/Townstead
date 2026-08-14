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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The brewing catalogue for a worksite: every beverage whose station stands inside this place.
 *
 * <p>A sibling of the cooking catalogue rather than a special case of it — which is the point. A
 * cafe's orders screen used to open empty, because the only catalogue in the registry knew about
 * food. Both walk the worksite's extent and both share {@link StationCatalogs}; the only difference
 * is which recipe list they read.</p>
 */
public final class BaristaOrderCatalog implements WorksiteCatalogs.Catalog {

    private BaristaOrderCatalog() {}

    public static void bootstrap() {
        WorksiteCatalogs.register(new BaristaOrderCatalog());
    }

    @Override
    public ResourceLocation taskType() {
        return com.aetherianartificer.townstead.profession.def.WorkTaskTypes.BREW;
    }

    @Override
    public List<Option> optionsFor(ServerLevel level, Worksite site) {
        Set<Long> extent = Worksites.extentOf(level, site);
        if (extent.isEmpty()) return List.of();
        Set<StationType> present = StationCatalogs.stationsIn(level, extent);
        if (present.isEmpty()) return List.of();

        Map<ResourceLocation, Integer> onHand = StationCatalogs.stockIn(level, site, extent);
        Set<ResourceLocation> presentDefs = StationCatalogs.stationDefsIn(level, extent);
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        List<Option> out = new ArrayList<>();
        for (StationType type : present) {
            for (DiscoveredRecipe recipe : WorkRecipeRegistry.getBeverageRecipesForStation(level, type)) {
                List<com.aetherianartificer.townstead.work.station.WorkstationDef> declared =
                        WorkRecipeRegistry.defsFor(recipe);
                com.aetherianartificer.townstead.work.station.WorkstationDef station = declared.stream()
                        .filter(def -> presentDefs.contains(def.id())).findFirst().orElse(null);
                if (!declared.isEmpty() && station == null) continue;
                if (!seen.add(recipe.output())) continue;
                out.add(StationCatalogs.optionFrom(recipe, type, station, onHand));
            }
        }
        return out;
    }

    @Override
    public List<Station> stationsFor(ServerLevel level, Worksite site) {
        // The cooking catalogue already reports the stations standing here, and they are the same
        // blocks. Reporting them twice would only give the screen two of everything.
        return List.of();
    }
}
