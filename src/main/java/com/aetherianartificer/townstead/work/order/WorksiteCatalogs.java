package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.Townstead;
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
    /** Last emitted Order Sheet shape per worksite; watcher refreshes should not repeat diagnostics. */
    private static final java.util.Map<Long, String> LAST_DIAGNOSTIC =
            new java.util.concurrent.ConcurrentHashMap<>();

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
        boolean hasDrivers = com.aetherianartificer.townstead.work.site.WorksiteDrivers
                .supportsDrivers(level, site);
        boolean driverAvailable = !hasDrivers
                || com.aetherianartificer.townstead.work.site.WorksiteDrivers
                        .assignmentAvailable(level, site);

        Set<net.minecraft.resources.ResourceLocation> seen = new LinkedHashSet<>();
        List<Option> out = new ArrayList<>();
        StringBuilder diagnostic = new StringBuilder("worked=").append(worked);
        for (Catalog catalog : CATALOGS) {
            net.minecraft.resources.ResourceLocation type = catalog.taskType();
            if (type != null && !worked.contains(type)) {
                diagnostic.append(", ").append(catalog.getClass().getSimpleName())
                        .append("=skipped(").append(type).append(')');
                continue;
            }
            try {
                List<Option> offered = catalog.optionsFor(level, site);
                diagnostic.append(", ").append(catalog.getClass().getSimpleName())
                        .append('=').append(offered.size());
                for (Option option : offered) {
                    if (option == null || !seen.add(option.output())) continue;
                    // Gated here, once, so no catalogue has to remember: what counts as cannibal
                    // fare is a tag, and whether it is served is a setting.
                    if (!option.activity() && !OrderTags.permitted(option.output())) continue;
                    out.add(withDriverAvailability(site, option, driverAvailable));
                }
            } catch (Throwable failure) {
                // One engine's catalogue failing must not close the screen for the others.
                Townstead.LOGGER.error("Order Sheet provider {} failed for worksite {}",
                        catalog.getClass().getSimpleName(), site.id(), failure);
            }
        }
        addCategories(out);
        diagnostic.append(", final=").append(out.size());
        String summary = diagnostic.toString();
        if (!summary.equals(LAST_DIAGNOSTIC.put(site.id(), summary))) {
            Townstead.LOGGER.info("Order Sheet option summary for worksite {}: {}", site.id(), summary);
        }
        return List.copyOf(out);
    }

    private static Option withDriverAvailability(Worksite site, Option option,
                                                 boolean driverAvailable) {
        if (option.activity() || option.tag()) return option;
        var def = com.aetherianartificer.townstead.work.station.Workstations
                .v2ByBlockId(option.stationIcon());
        if (def == null || !def.hasReservation() || driverAvailable) return option;
        String blocker = site.driver() == null
                ? "No matching assignment is currently available."
                : "The assigned entity is unavailable or no longer matches this station.";
        return new Option(option.output(), option.stationLabel(), option.stationIcon(),
                false, blocker, option.makes(), option.needs(), option.missing(),
                option.activity(), option.tag(), option.label(), option.commission(),
                option.operated());
    }

    /**
     * One entry per declared category ({@code orders/} item tags) that something offered here
     * belongs to. Synthesised over the gathered options rather than declared by any one catalogue,
     * because "any cooked meat" spans whatever trades happen to work this place.
     */
    private static void addCategories(List<Option> out) {
        if (out.isEmpty()) return;
        // Sorted, so which of two equivalent sets wins is the same on every run and every client.
        // An unordered registry walk deciding a visible name is the bug this file has already had
        // once, in WorksiteBindings.
        List<net.minecraft.resources.ResourceLocation> tagIds =
                new ArrayList<>(OrderTags.categories());
        tagIds.sort(java.util.Comparator.comparing(net.minecraft.resources.ResourceLocation::toString));

        // What each surviving category offers HERE, so a later one covering exactly the same
        // things can be recognised as the same kind under another name.
        java.util.Map<Set<net.minecraft.resources.ResourceLocation>,
                net.minecraft.resources.ResourceLocation> byCoverage = new java.util.HashMap<>();
        List<Option> categories = new ArrayList<>();
        for (net.minecraft.resources.ResourceLocation tagId : tagIds) {
            Set<net.minecraft.resources.ResourceLocation> covered = new LinkedHashSet<>();
            Option first = null;
            boolean available = false;
            for (Option option : out) {
                if (option.activity() || option.tag()) continue;
                if (!OrderTags.contains(tagId, option.output())) continue;
                if (first == null) first = option;
                available |= option.available();
                covered.add(option.output());
            }
            if (first == null) continue;
            // Merge, rather than list the same shelf twice: two sets that offer exactly the same
            // things at this worksite are indistinguishable to whoever is reading the sheet, so
            // the first (sorted) name stands for both. Only EQUAL coverage merges — a narrower
            // kind inside a broader one is a real distinction ("any soup" vs "any meal") and
            // absorbing it would delete a set the player deliberately declared.
            if (byCoverage.putIfAbsent(covered, tagId) != null) continue;
            categories.add(Option.category(tagId, OrdersService.categoryLabel(tagId), first.output(),
                    available, available ? "" : first.blocker(),
                    available ? List.of() : first.missing()));
        }
        out.addAll(categories);
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
            } catch (Throwable failure) {
                // Same bargain as the options: one engine failing must not close the screen.
                Townstead.LOGGER.error("Station catalogue {} failed for worksite {}",
                        catalog.getClass().getSimpleName(), site.id(), failure);
            }
        }
        return List.copyOf(out);
    }
}
