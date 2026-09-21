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
        return new Entry(id, id, group, group, family, tier, Set.of(spirits), recognized, false, false, String.join(" ", spirits));
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
}
