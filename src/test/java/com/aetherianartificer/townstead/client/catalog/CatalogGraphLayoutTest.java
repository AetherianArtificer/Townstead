package com.aetherianartificer.townstead.client.catalog;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.aetherianartificer.townstead.client.catalog.CatalogGraphLayout.*;

class CatalogGraphLayoutTest {
    @Test void authoredPrefixesAndDifferentStageNamesKeepTheirTierNumbers() {
        assertEquals(2, tierNumber("custom/stage2", "custom/stage"));
        assertEquals(3, tierNumber("compat/bakery/bakery_l3", "compat/bakery/"));
        assertEquals(0, tierNumber("stage", "stage"));
        assertEquals(0, tierNumber("stage_l99999999999999", null));
    }
    private Entry entry(String id, String family, int tier, String group, boolean recognized, String... spirits) {
        return new Entry(id, id, group, group, family, tier, Set.of(spirits), recognized, false, false, String.join(" ", spirits), Map.of());
    }
    private Layout layout(List<Entry> entries, Grouping grouping, Sort sort, boolean descending, String query) {
        return build(entries, grouping, sort, descending, query, Filter.ALL,
                Map.of("nautical", 10, "industry", 80), s -> s, 430);
    }
    @Test void distinctNamedStagesStayConnectedAndSortedWhenSectorsReverse() {
        var stages = List.of(entry("Bakery", "bakers", 3, "Bakeries", false),
                entry("Bread Stand", "bakers", 1, "Bakeries", true), entry("Bake Sale", "bakers", 2, "Bakeries", false),
                entry("House", "", 0, "Core", false));
        var result = layout(stages, Grouping.GROUP, Sort.NAME, true, "");
        assertEquals(List.of("Core", "Bakeries"), result.sectors().stream().map(Sector::id).toList());
        assertEquals(List.of(1, 2, 3), result.sectors().get(1).nodes().stream().map(n -> n.entry().tier()).toList());
        assertEquals(2, result.sectors().get(1).edges().size());
    }
    @Test void searchKeepsNeighboringTiersAsContextAndCountsOnlyMatches() {
        var result = layout(List.of(entry("Landing", "docks", 1, "Docks", false),
                entry("Pier", "docks", 2, "Docks", true), entry("Wharf", "docks", 3, "Docks", false)),
                Grouping.GROUP, Sort.NAME, false, "WHARF");
        assertEquals(1, result.matches()); assertEquals(3, result.nodes().size());
        assertEquals(2, result.sectors().get(0).edges().size());
        assertEquals(List.of(false, false, true), result.nodes().stream().map(Node::match).toList());
    }
    @Test void spiritMembershipIsManyToManyAndUsesVillagePointsForSort() {
        var result = layout(List.of(entry("Wharf", "docks", 3, "Docks", false, "nautical", "industry")),
                Grouping.SPIRIT, Sort.POINTS, true, "");
        assertEquals(List.of("industry", "nautical"), result.sectors().stream().map(Sector::id).toList());
        assertEquals(1, result.matches()); assertEquals(2, result.nodes().size());
    }
    @Test void modGroupsUseDisplayNamesAndCountSharedProvidersOnlyOnce() {
        var mods = Map.of("bakery", "Bakery", "farmersdelight", "Farmer's Delight");
        var first = new Entry("bread_l1", "Bread Stand", "bakers", "Bakeries", "bread", 1,
                Set.of(), true, false, false, "", mods);
        var second = new Entry("bread_l2", "Bakery", "bakers", "Bakeries", "bread", 2,
                Set.of(), false, false, false, "", mods);
        var result = layout(List.of(first, second, entry("House", "", 0, "Core", false)),
                Grouping.MOD, Sort.NAME, false, "");
        assertEquals(List.of("Bakery", "Farmer's Delight", "~core"), result.sectors().stream().map(Sector::label).toList());
        assertEquals(3, result.matches());
        assertEquals(5, result.nodes().size());
        assertEquals(2, result.sectors().stream().mapToInt(s -> s.edges().size()).sum());
        var reversed = layout(List.of(first, second), Grouping.MOD, Sort.NAME, true, "");
        assertEquals(List.of("farmersdelight", "bakery"), reversed.sectors().stream().map(Sector::id).toList());
        assertTrue(matches(first, "farmer's delight", Filter.RECOGNIZED));
        assertTrue(matches(first, "farmersdelight", Filter.ALL));
    }

    @Test void modGroupingRetainsNeighboringTiersWhenSearchingAndFiltering() {
        var first = new Entry("bread_l1", "Bread Stand", "bakers", "Bakeries", "bread", 1,
                Set.of(), true, false, false, "", Map.of("bakery", "Bakery"));
        var second = new Entry("bread_l2", "Bake Sale", "bakers", "Bakeries", "bread", 2,
                Set.of(), false, false, false, "", Map.of("bakery", "Bakery"));
        var result = build(List.of(first, second), Grouping.MOD, Sort.NAME, false, "bread", Filter.RECOGNIZED,
                Map.of(), s -> s, 430);
        assertEquals(1, result.matches());
        assertEquals(List.of(true, false), result.nodes().stream().map(Node::match).toList());
        assertEquals(1, result.sectors().get(0).edges().size());
    }
    @Test void missingTierDoesNotInventAnEdgeAndIndependentSetsNeverConnect() {
        var result = layout(List.of(entry("One", "chain", 1, "Group", false), entry("Three", "chain", 3, "Group", false),
                entry("Well", "", 0, "Group", true), entry("Fountain", "", 0, "Group", false)), Grouping.GROUP, Sort.NAME, false, "");
        assertTrue(result.sectors().get(0).edges().isEmpty());
    }
    @Test void recognizedSortUsesWholeSectorMembershipBeforeSearch() {
        var result = layout(List.of(entry("A Match", "", 0, "A", false), entry("A Other", "", 0, "A", true),
                entry("B Match", "", 0, "B", false)), Grouping.GROUP, Sort.RECOGNIZED, true, "Match");
        assertEquals("A", result.sectors().get(0).id()); assertEquals(1, result.sectors().get(0).recognized());
        assertEquals(2, result.matches());
    }
    @Test void filterEmptyAndMultitermSearchAreExplicit() {
        Entry entry = entry("Wharf", "docks", 3, "Docks", true, "nautical");
        assertTrue(matches(entry, "docks nautical", Filter.RECOGNIZED));
        assertFalse(matches(entry, "docks", Filter.HANGOUT));
        assertEquals(0, layout(List.of(entry), Grouping.GROUP, Sort.NAME, false, "unknown").matches());
    }
    @Test void longChainsAndSectorsDoNotOverlapAtNarrowWidths() {
        List<Entry> entries = new ArrayList<>();
        for (int t = 1; t <= 8; t++) entries.add(entry("Tier " + t, "family", t, "A", false));
        entries.add(entry("Other", "", 0, "B", false));
        var result = build(entries, Grouping.GROUP, Sort.NAME, false, "", Filter.ALL, Map.of(), s -> s, 170);
        var a = result.sectors().get(0); var b = result.sectors().get(1);
        assertTrue(b.y() >= a.y() + a.height()); assertEquals(7, a.edges().size());
        assertEquals(8, a.nodes().stream().map(Node::x).distinct().count());
    }

    @Test void mixedCatalogBalancesTheOverviewAgainstBothViewportDimensions() {
        var entries = mixedCatalog();
        var result = build(entries, Grouping.GROUP, Sort.NAME, false, "", Filter.ALL, Map.of(), s -> s, 560, 310);
        double fit = Math.min(560.0 / result.width(), 310.0 / result.height());
        assertTrue(result.width() * fit > 560 * 0.8, "Overview should use the horizontal space");
        assertTrue(result.height() * fit > 310 * 0.8, "Overview should use the vertical space");
        assertEquals(entries.size(), result.matches());
        assertEquals(entries.size(), result.nodes().size());
        for (var a : result.sectors()) {
            for (var b : result.sectors()) {
                if (a == b) continue;
                assertTrue(a.x() + a.width() <= b.x() || b.x() + b.width() <= a.x()
                        || a.y() + a.height() <= b.y() || b.y() + b.height() <= a.y());
            }
        }
        var core = result.sectors().stream().filter(s -> s.id().equals("Core")).findFirst().orElseThrow();
        assertTrue(core.nodes().stream().map(Node::x).distinct().count() > 5);
    }

    @Test void viewportShapeChangesPackingWithoutChangingSortOrTierChains() {
        var entries = mixedCatalog();
        var wide = build(entries, Grouping.GROUP, Sort.NAME, true, "", Filter.ALL, Map.of(), s -> s, 800, 300);
        var tall = build(entries, Grouping.GROUP, Sort.NAME, true, "", Filter.ALL, Map.of(), s -> s, 300, 800);
        assertTrue(wide.width() / (double) wide.height() > tall.width() / (double) tall.height());
        assertEquals(wide.sectors().stream().map(Sector::id).toList(), tall.sectors().stream().map(Sector::id).toList());
        assertEquals(40, wide.sectors().stream().mapToInt(s -> s.edges().size()).sum());
        assertEquals(wide, build(entries, Grouping.GROUP, Sort.NAME, true, "", Filter.ALL, Map.of(), s -> s, 800, 300));
    }

    @Test void loneBuildingIsCenteredInsideItsGroupBox() {
        var result = layout(List.of(entry("Beach Club", "", 0, "Beachparty", false)), Grouping.GROUP, Sort.NAME, false, "");
        var sector = result.sectors().get(0);
        var node = sector.nodes().get(0);
        assertEquals(node.x() - sector.x(), sector.x() + sector.width() - node.x() - 26);
    }

    private List<Entry> mixedCatalog() {
        List<Entry> entries = new ArrayList<>();
        for (int group = 0; group < 20; group++) {
            String id = "Group " + String.format(Locale.ROOT, "%02d", group);
            for (int tier = 1; tier <= 3; tier++) entries.add(entry(id + " tier " + tier, id, tier, id, false));
        }
        for (int i = 0; i < 32; i++) entries.add(entry("Independent " + i, "", 0, "Core", false));
        return entries;
    }
}
