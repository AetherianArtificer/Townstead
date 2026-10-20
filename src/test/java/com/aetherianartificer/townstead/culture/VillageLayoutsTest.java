package com.aetherianartificer.townstead.culture;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageLayoutsTest {
    private static final ResourceLocation COTTAGE = id("cottage");
    private static final ResourceLocation SHACK = id("shack");
    private static final ResourceLocation CHAPEL = id("chapel");
    private static final ResourceLocation FARM = id("farm");
    private static final Function<ResourceLocation, ResourceLocation> SELF = Function.identity();

    @AfterEach
    void clear() {
        VillageLayouts.replace(Map.of());
        VillageLayouts.replaceStages(Map.of());
    }

    @Test
    void dropsBuildingsAtTheirMaximum() {
        VillageLayouts.replace(Map.of(CHAPEL, 1));
        assertEquals(List.of(COTTAGE), VillageLayouts.arrange(List.of(CHAPEL, COTTAGE), SELF, Map.of(CHAPEL, 1)));
    }

    @Test
    void triesBuildingsShortOfTheirMinimumFirst() {
        VillageLayouts.replace(Map.of(), Map.of(FARM, 2), List.of());
        assertEquals(List.of(FARM, COTTAGE, CHAPEL),
                VillageLayouts.arrange(List.of(COTTAGE, FARM, CHAPEL), SELF, Map.of(FARM, 1)));
        assertEquals(List.of(COTTAGE, FARM, CHAPEL),
                VillageLayouts.arrange(List.of(COTTAGE, FARM, CHAPEL), SELF, Map.of(FARM, 2)));
    }

    @Test
    void groupsCountEveryBuildingTheyHold() {
        var houses = new VillageLayouts.Group("house", Set.of(COTTAGE, SHACK), 3, 4);
        VillageLayouts.replace(Map.of(), Map.of(), List.of(houses));
        // Short of the group minimum: either house comes first, in the order given.
        assertEquals(List.of(SHACK, COTTAGE, CHAPEL),
                VillageLayouts.arrange(List.of(CHAPEL, SHACK, COTTAGE), SELF, Map.of(COTTAGE, 2)));
        // Group full: no more houses of any kind.
        assertEquals(List.of(CHAPEL),
                VillageLayouts.arrange(List.of(CHAPEL, SHACK, COTTAGE), SELF, Map.of(COTTAGE, 3, SHACK, 1)));
    }

    @Test
    void triesTheBuildingNearestItsMinimumFirst() {
        var houses = new VillageLayouts.Group("house", Set.of(COTTAGE), 4, null);
        var temple = new VillageLayouts.Group("temple", Set.of(CHAPEL), 1, 1);
        VillageLayouts.replace(Map.of(), Map.of(), List.of(houses, temple));
        assertEquals(List.of(CHAPEL, COTTAGE),
                VillageLayouts.arrange(List.of(COTTAGE, CHAPEL), SELF, Map.of(COTTAGE, 1)));
    }

    @Test
    void leavesUnknownTemplatesAlone() {
        VillageLayouts.replace(Map.of(CHAPEL, 0));
        List<ResourceLocation> streets = List.of(id("streets/straight"), id("streets/corner"));
        assertEquals(streets, VillageLayouts.arrange(streets, SELF, Map.of()));
    }

    @Test
    void readsGroups() {
        var group = VillageLayouts.Loader.group(JsonParser.parseString(
                "{\"name\":\"house\",\"templates\":[\"test:village/cottage\"],\"min\":4}").getAsJsonObject());
        assertEquals(new VillageLayouts.Group("house", Set.of(COTTAGE), 4, null), group);
        assertThrows(IllegalArgumentException.class, () -> VillageLayouts.Loader.group(JsonParser.parseString(
                "{\"name\":\"farm\",\"templates\":[\"test:village/farm\"],\"min\":3,\"max\":1}").getAsJsonObject()));
    }

    @Test
    void aPlannedStageUsesItsOwnRanges() {
        VillageLayouts.replace(Map.of(CHAPEL, 3));
        var hamlet = new VillageLayouts.Stage("hamlet", 1, 3, 40,
                VillageLayouts.Ranges.of(Map.of(CHAPEL, 0), Map.of(), List.of()), false);
        VillageLayouts.beginPlanning(hamlet);
        try {
            assertEquals(List.of(COTTAGE), VillageLayouts.arrange(List.of(CHAPEL, COTTAGE), SELF, Map.of()));
        } finally {
            VillageLayouts.endPlanning();
        }
        assertEquals(List.of(CHAPEL, COTTAGE), VillageLayouts.arrange(List.of(CHAPEL, COTTAGE), SELF, Map.of()));
    }

    @Test
    void rollsTheSameStageForTheSamePlace() {
        ResourceLocation town = id("town");
        var small = stage("hamlet", 3);
        var large = stage("city", 1);
        VillageLayouts.replaceStages(Map.of(town, List.of(small, large)));
        int smallCount = 0;
        for (int x = 0; x < 400; x++) {
            var rolled = VillageLayouts.stage(town, 1234L, x, -x * 3);
            assertSame(rolled, VillageLayouts.stage(town, 1234L, x, -x * 3));
            if (rolled == small) smallCount++;
        }
        // Weighted 3 to 1: about 300 of 400.
        assertTrue(smallCount > 250 && smallCount < 350, "hamlets rolled: " + smallCount);
        assertNull(VillageLayouts.stage(id("unstaged"), 1234L, 0, 0));
    }

    @Test
    void readsStages() {
        var stage = VillageLayouts.Loader.stage(JsonParser.parseString(
                "{\"name\":\"hamlet\",\"weight\":2,\"size\":3,\"max_distance\":40,\"max\":{\"test:village/chapel\":0},"
                        + "\"groups\":[{\"name\":\"house\",\"templates\":[\"test:village/cottage\"],\"min\":2,\"max\":4}],"
                        + "\"enclosure\":false}").getAsJsonObject());
        assertEquals("hamlet", stage.name());
        assertEquals(40, stage.maxDistance());
        assertEquals(0, stage.ranges().max().get(CHAPEL));
        assertEquals(1, stage.ranges().groups().get(COTTAGE).size());
        assertFalse(stage.walled());
        assertThrows(IllegalArgumentException.class, () -> VillageLayouts.Loader.stage(JsonParser.parseString(
                "{\"name\":\"huge\",\"size\":3,\"max_distance\":200}").getAsJsonObject()));
    }

    private static VillageLayouts.Stage stage(String name, int weight) {
        return new VillageLayouts.Stage(name, weight, 5, 64, VillageLayouts.Ranges.NONE, true);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.tryParse("test:village/" + path);
    }
}
