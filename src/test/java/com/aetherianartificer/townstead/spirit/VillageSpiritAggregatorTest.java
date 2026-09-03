package com.aetherianartificer.townstead.spirit;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VillageSpiritAggregatorTest {
    @Test
    void remainsASettlementUntilASpiritReachesTierOne() {
        SpiritReadout readout = readout(points("pastoral", 24, "commercial", 24));

        assertEquals(SpiritReadout.Classification.SETTLEMENT, readout.classification());
        assertEquals(0, readout.tierIndex());
        assertNull(readout.primarySpiritId());
        assertNull(readout.secondarySpiritId());
    }

    @Test
    void equalEstablishedSpiritsProduceABlendBeforeSingleDominance() {
        SpiritReadout readout = readout(points("pastoral", 30, "commercial", 30));

        assertEquals(SpiritReadout.Classification.BLEND, readout.classification());
        assertEquals(1, readout.tierIndex());
        assertEquals("pastoral", readout.primarySpiritId());
        assertEquals("commercial", readout.secondarySpiritId());
        assertEquals("townstead.spirit.blend.pastoral.commercial", readout.translationKey());
    }

    @Test
    void secondSpiritMustIndependentlyReachTierOneToCreateABlend() {
        SpiritReadout readout = readout(points("pastoral", 75, "commercial", 25));

        assertEquals(SpiritReadout.Classification.BLEND, readout.classification());
        assertEquals(2, readout.tierIndex());

        SpiritReadout belowTier = readout(points("pastoral", 72, "commercial", 24));
        assertEquals(SpiritReadout.Classification.SINGLE, belowTier.classification());
        assertEquals("pastoral", belowTier.primarySpiritId());
    }

    @Test
    void dominantSpiritRemainsSingleWhenRunnerUpMissesBlendShare() {
        SpiritReadout readout = readout(points(
                "pastoral", 60,
                "commercial", 20,
                "industrious", 20));

        assertEquals(SpiritReadout.Classification.SINGLE, readout.classification());
        assertEquals(2, readout.tierIndex());
        assertEquals("pastoral", readout.primarySpiritId());
        assertNull(readout.secondarySpiritId());
    }

    @Test
    void threeWayVillageBlendsItsTwoStrongestEstablishedSpirits() {
        SpiritReadout readout = readout(points(
                "nautical", 40,
                "pastoral", 30,
                "commercial", 30));

        assertEquals(SpiritReadout.Classification.BLEND, readout.classification());
        assertEquals("nautical", readout.primarySpiritId());
        assertEquals("pastoral", readout.secondarySpiritId());
    }

    @Test
    void diffuseVillageWithoutTwoQuarterSharesIsMixed() {
        SpiritReadout readout = readout(points(
                "nautical", 39,
                "pastoral", 24,
                "martial", 24,
                "scholar", 23));

        assertEquals(SpiritReadout.Classification.MIXED, readout.classification());
        assertEquals(1, readout.tierIndex());
        assertNull(readout.primarySpiritId());
        assertNull(readout.secondarySpiritId());
    }

    @Test
    void tierThresholdsAreInclusiveAndDefensivelyCopied() {
        assertArrayEquals(new int[]{25, 60, 140, 300, 600},
                VillageSpiritAggregator.tierThresholds());
        int[] thresholds = VillageSpiritAggregator.tierThresholds();
        thresholds[0] = 999;
        assertArrayEquals(new int[]{25, 60, 140, 300, 600},
                VillageSpiritAggregator.tierThresholds());

        int[] expected = {0, 1, 1, 2, 2, 3, 3, 4, 4, 5};
        int[] samples = {24, 25, 59, 60, 139, 140, 299, 300, 599, 600};
        for (int i = 0; i < samples.length; i++) {
            assertEquals(expected[i], VillageSpiritAggregator.tierForSpirit(samples[i]),
                    "unexpected tier at " + samples[i] + " points");
        }
    }

    @Test
    void everyRegistryOrderedPairCanActuallyProduceItsBlend() {
        var spirits = SpiritRegistry.ordered();
        int pairs = 0;
        for (int first = 0; first < spirits.size(); first++) {
            for (int second = first + 1; second < spirits.size(); second++) {
                String firstId = spirits.get(first).id();
                String secondId = spirits.get(second).id();
                SpiritReadout readout = readout(points(firstId, 25, secondId, 25));

                assertEquals(SpiritReadout.Classification.BLEND, readout.classification(),
                        firstId + " + " + secondId + " should classify as a blend");
                assertEquals(firstId, readout.primarySpiritId());
                assertEquals(secondId, readout.secondarySpiritId());
                assertEquals("townstead.spirit.blend." + firstId + "." + secondId,
                        readout.translationKey());
                pairs++;
            }
        }
        assertEquals(66, pairs);
    }

    private static SpiritReadout readout(Map<String, Integer> points) {
        int total = points.values().stream().mapToInt(Integer::intValue).sum();
        return VillageSpiritAggregator.readoutFor(new SpiritTotals(points, total, 0));
    }

    private static Map<String, Integer> points(Object... entries) {
        Map<String, Integer> points = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            points.put((String) entries[i], (Integer) entries[i + 1]);
        }
        return Map.copyOf(points);
    }
}
