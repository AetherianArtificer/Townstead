package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Option;
import com.aetherianartificer.townstead.work.site.Worksite;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What a worksite can be asked to make, answered by whichever engines work there.
 *
 * <p>A registry rather than a lookup, for the same reason everything else here is one: the order
 * screen must not learn that Farmer's Delight exists. A cook's compat registers the cooking
 * catalogue, a smith's would register its own, and the screen simply asks the worksite.</p>
 */
public final class WorksiteCatalogs {

    /** Answers what one kind of work could produce at this place. */
    public interface Catalog {

        /**
         * The work-task type this catalogue speaks for, or null to be asked everywhere.
         *
         * <p>Checked against the trades that claim the worksite, so a butchery is not offered a
         * kitchen's recipes just because it happens to contain a furnace. Null is for catalogues
         * that already decide relevance for themselves.</p>
         */
        default net.minecraft.resources.ResourceLocation taskType() {
            return null;
        }

        List<Option> optionsFor(ServerLevel level, Worksite site);

        /**
         * The kinds of workstation this engine cares about, and whether this worksite has one.
         * Default empty so an engine that has nothing to say about stations says nothing.
         */
        default List<com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Station>
                stationsFor(ServerLevel level, Worksite site) {
            return List.of();
        }
    }

    private static final List<Catalog> CATALOGS = new CopyOnWriteArrayList<>();

    private WorksiteCatalogs() {}

    public static void register(Catalog catalog) {
        if (catalog != null) CATALOGS.add(catalog);
    }

    /**
     * Everything anybody could make here, de-duplicated by output. Two engines offering the same
     * item is a fact about the village, not two things to order.
     */
    public static List<Option> optionsFor(ServerLevel level, Worksite site) {
        if (CATALOGS.isEmpty()) return List.of();
        // What is done here, not what could be: a place no trade claims has nobody to order.
        Set<net.minecraft.resources.ResourceLocation> worked =
                com.aetherianartificer.townstead.work.site.WorksiteWork.typesAt(level, site,
                        com.aetherianartificer.townstead.work.site.Worksites.extentOf(level, site));

        Set<net.minecraft.resources.ResourceLocation> seen = new LinkedHashSet<>();
        List<Option> out = new ArrayList<>();
        for (Catalog catalog : CATALOGS) {
            net.minecraft.resources.ResourceLocation type = catalog.taskType();
            if (type != null && !worked.contains(type)) continue;
            try {
                for (Option option : catalog.optionsFor(level, site)) {
                    if (option == null || !seen.add(option.output())) continue;
                    // Gated here, once, so no catalogue has to remember: what counts as cannibal
                    // fare is a tag, and whether it is served is a setting.
                    if (!option.activity() && !OrderTags.permitted(option.output())) continue;
                    out.add(option);
                }
            } catch (Throwable ignored) {
                // One engine's catalogue failing must not close the screen for the others.
            }
        }
        addCategories(out);
        return List.copyOf(out);
    }

    /**
     * One entry per declared category ({@code orders/} item tags) that something offered here
     * belongs to. Synthesised over the gathered options rather than declared by any one catalogue,
     * because "any cooked meat" spans whatever trades happen to work this place.
     */
    private static void addCategories(List<Option> out) {
        if (out.isEmpty()) return;
        for (net.minecraft.resources.ResourceLocation tagId : OrderTags.categories()) {
            Option first = null;
            boolean available = false;
            for (Option option : out) {
                if (option.activity() || option.tag()) continue;
                if (!OrderTags.contains(tagId, option.output())) continue;
                if (first == null) first = option;
                available |= option.available();
                if (available) break;
            }
            if (first == null) continue;
            out.add(Option.category(tagId, OrdersService.categoryLabel(tagId), first.output(),
                    available, available ? "" : first.blocker()));
        }
    }

    /** Every engine's stations, de-duplicated by label. */
    public static List<com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Station>
            stationsFor(ServerLevel level, Worksite site) {
        if (CATALOGS.isEmpty()) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        List<com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Station> out =
                new ArrayList<>();
        for (Catalog catalog : CATALOGS) {
            try {
                for (var station : catalog.stationsFor(level, site)) {
                    if (station != null && seen.add(station.label())) out.add(station);
                }
            } catch (Throwable ignored) {
                // Same bargain as the options: one engine failing must not close the screen.
            }
        }
        return List.copyOf(out);
    }
}
