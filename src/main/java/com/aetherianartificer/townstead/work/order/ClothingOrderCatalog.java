package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.clothing.ClothingDefs;
import com.aetherianartificer.townstead.clothing.ClothingEntry;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.StationType;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Option;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Station;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.WorksiteWork;
import com.aetherianartificer.townstead.work.site.Worksites;
import com.aetherianartificer.townstead.work.station.WorkstationDef;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the Tailor can be asked for: every discovered crafting-station recipe whose output the
 * clothing engine describes, or that its station declares orderable outright. The Order Sheet
 * never learns which mod sewed it.
 */
public final class ClothingOrderCatalog implements WorksiteCatalogs.Catalog {

    private ClothingOrderCatalog() {}

    public static void bootstrap() {
        WorksiteCatalogs.register(new ClothingOrderCatalog());
    }

    @Override
    public ResourceLocation taskType() {
        return WorkTaskTypes.CRAFT;
    }

    @Override
    public List<Option> optionsFor(ServerLevel level, Worksite site) {
        Set<Long> extent = Worksites.extentOf(level, site);
        if (extent.isEmpty()) return List.of();
        Set<StationType> present = StationCatalogs.stationsIn(level, extent);
        if (present.isEmpty()) return List.of();

        Map<ResourceLocation, Integer> onHand = StationCatalogs.stockIn(level, site, extent);
        Set<ResourceLocation> presentDefs = StationCatalogs.stationDefsIn(level, extent);
        List<WorkTaskDef> declarations = WorksiteWork.declaredTasksAt(level, site, extent, WorkTaskTypes.CRAFT);
        if (declarations.isEmpty()) return List.of();
        String buildingType = WorksiteWork.buildingTypeOf(level, site);

        Map<ResourceLocation, Option> out = new java.util.LinkedHashMap<>();
        for (StationType type : present) {
            for (DiscoveredRecipe recipe : WorkRecipeRegistry.getRecipesForStation(level, type)) {
                if (!BuildingRecipeScopes.allows(buildingType, recipe.id())) continue;
                List<WorkstationDef> stations = RecipeOrderCatalogGate.matchingStations(
                        type, recipe, WorkRecipeRegistry.defsFor(recipe), presentDefs);
                WorkstationDef station = stations.stream()
                        .filter(def -> RecipeOrderCatalogGate.allowedByAny(declarations, def, recipe))
                        .findFirst().orElse(null);
                if (station == null) continue;
                // A copying or modifying line is a commission on a handed-over piece; the station
                // catalogue offers it with the hand-over gesture, never as a plain line here.
                var produce = com.aetherianartificer.townstead.work.station.StationProtocols
                        .produceFor(station, recipe);
                if (produce != null && (produce.copies() != null || produce.modifies() != null)) continue;
                if (!clothing(level, recipe.output(), station.orderable())) continue;
                Option option = StationCatalogs.optionFrom(recipe, type, station, onHand);
                out.merge(option.product(), option, Option::merge);
            }
        }
        return new ArrayList<>(out.values());
    }

    /** An output is clothing when an entry describes it; a station may also say "all". */
    static boolean clothing(ServerLevel level, ResourceLocation output, WorkstationDef.Orderable orderable) {
        if (output == null || !BuiltInRegistries.ITEM.containsKey(output)) return false;
        if (orderable != null && orderable.all()) return true;
        ClothingEntry entry = ClothingDefs.documented(level, new ItemStack(BuiltInRegistries.ITEM.get(output)));
        return entry != null;
    }

    @Override
    public List<Station> stationsFor(ServerLevel level, Worksite site) {
        Set<Long> extent = Worksites.extentOf(level, site);
        Set<StationType> roles = EnumSet.noneOf(StationType.class);
        Set<WorkstationDef> declared = new LinkedHashSet<>();
        for (StationType type : StationType.values()) {
            for (DiscoveredRecipe recipe : WorkRecipeRegistry.getRecipesForStation(level, type)) {
                List<WorkstationDef> stations = WorkRecipeRegistry.defsFor(recipe);
                boolean any = false;
                for (WorkstationDef def : stations) {
                    if (clothing(level, recipe.output(), def.orderable())) {
                        declared.add(def);
                        any = true;
                    }
                }
                if (!any && stations.isEmpty() && clothing(level, recipe.output(), null)) roles.add(type);
            }
        }
        List<Station> out = new ArrayList<>(StationCatalogs.stationList(level, extent, roles));
        out.addAll(StationCatalogs.declaredStationList(level, extent, declared));
        return out;
    }
}
