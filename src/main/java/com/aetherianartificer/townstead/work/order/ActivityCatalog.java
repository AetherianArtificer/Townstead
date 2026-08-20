package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.WorkActivities;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Option;
import com.aetherianartificer.townstead.work.site.Worksite;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Offers the jobs a worksite can be told to prefer, alongside the things it can make.
 *
 * <p>Registered like any other catalogue, so the screen asks one registry and gets both kinds of
 * line. Each job says for itself which places it belongs to — an earlier version offered all of
 * them everywhere, which put slaughtering on a kitchen's list and invited a player to write a line
 * nobody there would ever work.</p>
 *
 * <p>Relevance is asked of the <em>place</em>, not of whoever is standing in it: a shop with nobody
 * hired yet is still exactly the place you write a butcher's list for.</p>
 */
public final class ActivityCatalog implements WorksiteCatalogs.Catalog {

    private ActivityCatalog() {}

    public static void bootstrap() {
        WorksiteCatalogs.register(new ActivityCatalog());
    }

    @Override
    public List<Option> optionsFor(ServerLevel level, Worksite site) {
        List<ResourceLocation> jobs = WorkActivities.at(level, site);
        if (jobs.isEmpty()) return List.of();
        // Each job registered its own icon — the sponge for mopping, the shears for golems.
        // Handing every one of them the sword made the list read as four kinds of slaughter.
        ResourceLocation fallback = BuiltInRegistries.ITEM.getKey(Items.IRON_SWORD);
        List<Option> out = new ArrayList<>(jobs.size());
        for (ResourceLocation job : jobs) {
            ResourceLocation icon = WorkActivities.iconOf(job);
            out.add(Option.job(job, WorkActivities.labelOf(job), icon != null ? icon : fallback));
        }
        return out;
    }
}
