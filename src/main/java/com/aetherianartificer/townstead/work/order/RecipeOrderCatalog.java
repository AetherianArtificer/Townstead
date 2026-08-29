package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Option;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Station;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.StationType;
import com.aetherianartificer.townstead.work.recipe.WorkOutputTags;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.WorksiteWork;
import com.aetherianartificer.townstead.work.site.Worksites;
import com.aetherianartificer.townstead.work.station.WorkstationDef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Catalogues discovered station recipes for any recipe-driven work task. */
public final class RecipeOrderCatalog implements WorksiteCatalogs.Catalog {
    private final ResourceLocation taskType;
    private final TagKey<Item> outputTag;
    private final boolean beverages;
    private final boolean reportsStations;

    private RecipeOrderCatalog(ResourceLocation taskType, TagKey<Item> outputTag,
                               boolean beverages, boolean reportsStations) {
        this.taskType = taskType;
        this.outputTag = outputTag;
        this.beverages = beverages;
        this.reportsStations = reportsStations;
    }

    public static void bootstrap() {
        WorksiteCatalogs.register(new RecipeOrderCatalog(
                WorkTaskTypes.COOK, WorkOutputTags.COOK, false, true));
        WorksiteCatalogs.register(new RecipeOrderCatalog(
                WorkTaskTypes.BREW, WorkOutputTags.BREW, true, false));
    }

    @Override
    public ResourceLocation taskType() {
        return taskType;
    }

    @Override
    public List<Option> optionsFor(ServerLevel level, Worksite site) {
        Set<Long> extent = Worksites.extentOf(level, site);
        if (extent.isEmpty()) return List.of();
        Set<StationType> present = StationCatalogs.stationsIn(level, extent);
        if (present.isEmpty()) return List.of();

        Map<ResourceLocation, Integer> onHand = StationCatalogs.stockIn(level, site, extent);
        Set<ResourceLocation> presentDefs = StationCatalogs.stationDefsIn(level, extent);
        List<WorkTaskDef> declarations = WorksiteWork.declaredTasksAt(
                level, site, extent, taskType);
        if (declarations.isEmpty()) return List.of();
        String buildingType = WorksiteWork.buildingTypeOf(level, site);

        Set<ResourceLocation> seen = new LinkedHashSet<>();
        List<Option> out = new ArrayList<>();
        for (StationType type : present) {
            for (DiscoveredRecipe recipe : recipesFor(level, type)) {
                if (!BuildingRecipeScopes.allows(buildingType, recipe.id())) continue;
                List<WorkstationDef> stations = RecipeOrderCatalogGate.matchingStations(
                        type, recipe, WorkRecipeRegistry.defsFor(recipe), presentDefs);
                WorkstationDef station = stations.stream()
                        .filter(def -> RecipeOrderCatalogGate.allowedByAny(
                                declarations, def, recipe))
                        .findFirst().orElse(null);
                if (station == null) continue;
                // Purification can return the same item with changed components, so no static
                // output tag can describe its result.
                if (!recipe.purification()
                        && !WorkOutputTags.allows(outputTag, recipe.output(),
                        station.orderable())) continue;
                if (!seen.add(recipe.output())) continue;
                out.add(StationCatalogs.optionFrom(recipe, type, station, onHand));
            }
        }
        return out;
    }

    private List<DiscoveredRecipe> recipesFor(ServerLevel level, StationType type) {
        return beverages
                ? WorkRecipeRegistry.getBeverageRecipesForStation(level, type)
                : WorkRecipeRegistry.getFoodRecipesForStation(level, type);
    }

    @Override
    public List<Station> stationsFor(ServerLevel level, Worksite site) {
        if (!reportsStations) return List.of();
        Set<Long> extent = Worksites.extentOf(level, site);
        Set<StationType> roles = EnumSet.noneOf(StationType.class);
        Set<WorkstationDef> declared = new LinkedHashSet<>();
        for (StationType type : StationType.values()) {
            for (DiscoveredRecipe recipe : recipesFor(level, type)) {
                List<WorkstationDef> stations = WorkRecipeRegistry.defsFor(recipe);
                if (!stations.isEmpty()) declared.addAll(stations); else roles.add(type);
            }
        }
        List<Station> out = new ArrayList<>(StationCatalogs.stationList(level, extent, roles));
        out.addAll(StationCatalogs.declaredStationList(level, extent, declared));
        return out;
    }
}
