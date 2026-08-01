package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry;
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
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        List<Option> out = new ArrayList<>();
        for (StationType type : present) {
            for (DiscoveredRecipe recipe : ModRecipeRegistry.getFoodRecipesForStation(level, type)) {
                // Stated, not inferred. The registry's "food" only means "not a beverage", so a
                // furnace's smelting lands in it, and no property of the recipe distinguishes a
                // baked potato from an iron ingot. What a cook may be asked for is a tag, which
                // any pack can extend.
                if (!com.aetherianartificer.townstead.work.recipe.WorkOutputTags
                        .allows(com.aetherianartificer.townstead.work.recipe.WorkOutputTags.COOK,
                                recipe.output())) {
                    continue;
                }
                if (!seen.add(recipe.output())) continue;
                out.add(StationCatalogs.option(recipe, type, onHand));
            }
        }
        return out;
    }

    @Override
    public List<Station> stationsFor(ServerLevel level, Worksite site) {
        // The roles this pack has cooking recipes for, asked rather than assumed: a role nothing
        // cooks at is not a gap in the kitchen, and now that other trades declare roles of their
        // own, listing all of them would tell a cook they are missing a grinder.
        Set<StationType> cookRoles = EnumSet.noneOf(StationType.class);
        for (StationType type : StationType.values()) {
            if (!ModRecipeRegistry.getFoodRecipesForStation(level, type).isEmpty()) cookRoles.add(type);
        }
        return StationCatalogs.stationList(level, Worksites.extentOf(level, site), cookRoles);
    }
}
