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
        Set<net.minecraft.resources.ResourceLocation> seen = new LinkedHashSet<>();
        List<Option> out = new ArrayList<>();
        for (Catalog catalog : CATALOGS) {
            try {
                for (Option option : catalog.optionsFor(level, site)) {
                    if (option != null && seen.add(option.output())) out.add(option);
                }
            } catch (Throwable ignored) {
                // One engine's catalogue failing must not close the screen for the others.
            }
        }
        return List.copyOf(out);
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
