package com.aetherianartificer.townstead.culture;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VillagePalettesTest {
    private static final Set<String> BLOCKS = Set.of(
            "oak_planks", "oak_log", "oak_wood", "stripped_oak_log", "stripped_oak_wood", "oak_leaves",
            "oak_stairs", "oak_slab", "oak_fence", "oak_door",
            "spruce_planks", "spruce_log", "spruce_wood", "stripped_spruce_log", "stripped_spruce_wood",
            "spruce_leaves", "spruce_stairs", "spruce_slab", "spruce_fence", "spruce_door",
            "crimson_planks", "crimson_stem", "crimson_hyphae", "stripped_crimson_stem", "crimson_stairs",
            "stone_bricks", "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall",
            "mud_bricks", "mud_brick_stairs", "mud_brick_slab", "mud_brick_wall",
            "cobblestone", "cobblestone_stairs", "cobblestone_slab", "cobblestone_wall",
            "deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab");

    @Test
    void woodFamiliesCarryEveryMember() {
        Map<String, String> swaps = VillagePalettes.family("oak", "spruce", BLOCKS::contains);
        assertEquals("spruce_planks", swaps.get("oak_planks"));
        assertEquals("stripped_spruce_log", swaps.get("stripped_oak_log"));
        assertEquals("spruce_fence", swaps.get("oak_fence"));
        assertEquals("spruce_door", swaps.get("oak_door"));
        assertEquals(10, swaps.size());
    }

    @Test
    void netherWoodsSwapStemsForLogs() {
        Map<String, String> swaps = VillagePalettes.family("oak", "crimson", BLOCKS::contains);
        assertEquals("crimson_stem", swaps.get("oak_log"));
        assertEquals("crimson_hyphae", swaps.get("oak_wood"));
        assertEquals("stripped_crimson_stem", swaps.get("stripped_oak_log"));
        assertFalse(swaps.containsKey("oak_fence"), "crimson_fence is not among the known blocks");
    }

    @Test
    void stoneFamiliesUseTheSingularStem() {
        Map<String, String> swaps = VillagePalettes.family("stone_bricks", "mud_bricks", BLOCKS::contains);
        assertEquals(Map.of("stone_bricks", "mud_bricks", "stone_brick_stairs", "mud_brick_stairs",
                "stone_brick_slab", "mud_brick_slab", "stone_brick_wall", "mud_brick_wall"), swaps);
        // deepslate tiles have no wall: that member is left out rather than swapped to nothing
        Map<String, String> tiles = VillagePalettes.family("cobblestone", "deepslate_tiles", BLOCKS::contains);
        assertEquals(3, tiles.size());
        assertFalse(tiles.containsKey("cobblestone_wall"));
    }

    @Test
    void laterPalettesSwapAgainOnTop() {
        Map<String, String> culture = Map.of("cobblestone", "stone_bricks", "oak_planks", "spruce_planks");
        Map<String, String> biome = Map.of("stone_bricks", "mud_bricks", "birch_log", "spruce_log");
        Map<String, String> layered = VillagePalettes.layer(culture, biome);
        assertEquals("mud_bricks", layered.get("cobblestone"));
        assertEquals("spruce_planks", layered.get("oak_planks"));
        assertEquals("mud_bricks", layered.get("stone_bricks"));
        assertEquals("spruce_log", layered.get("birch_log"));
    }

    @Test
    void swapsBackToTheOriginalCancelOut() {
        Map<String, String> layered = VillagePalettes.layer(Map.of("oak_planks", "spruce_planks"),
                Map.of("spruce_planks", "oak_planks"));
        assertEquals("oak_planks", layered.get("spruce_planks"));
        assertFalse(layered.containsKey("oak_planks"));
    }

    @Test
    void aBroadFallbackAboveLeavesASpecificSwapStanding() {
        // A birch forest is also deciduous: the birch palette sits below the deciduous fallback.
        Map<String, String> layered = VillagePalettes.layer(Map.of("spruce_planks", "birch_planks"),
                Map.of("spruce_planks", "oak_planks"));
        assertEquals("birch_planks", layered.get("spruce_planks"));
    }
}
