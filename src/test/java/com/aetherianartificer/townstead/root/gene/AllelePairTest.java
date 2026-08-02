package com.aetherianartificer.townstead.root.gene;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The legacy allele-pair splitter: the old persisted form joined the two allele
 * encodings with ";", which collides with the payload's own channel separator, so
 * the split must land on the semicolon in front of the second allele's gene id —
 * not the first semicolon in the string.
 */
class AllelePairTest {

    @Test
    void plainGeneIdPairSplitsAtTheOnlySeparator() {
        assertArrayEquals(new String[]{"townstead_roots:night_vision", "townstead_roots:night_vision"},
                AllelePair.splitLegacy("townstead_roots:night_vision;townstead_roots:night_vision"));
    }

    @Test
    void variantPairSplits() {
        assertArrayEquals(new String[]{"townstead_roots:beard#bushy", "townstead_roots:beard#trimmed"},
                AllelePair.splitLegacy("townstead_roots:beard#bushy;townstead_roots:beard#trimmed"));
    }

    @Test
    void bareFloatPayloadPairSplits() {
        assertArrayEquals(new String[]{"townstead_classic:elf_ears#1.030", "townstead_classic:elf_ears#1.010"},
                AllelePair.splitLegacy("townstead_classic:elf_ears#1.030;townstead_classic:elf_ears#1.010"));
    }

    @Test
    void singleChannelPairSplits() {
        assertArrayEquals(
                new String[]{"townstead_classic:elf_ears#length=1.030", "townstead_classic:elf_ears#length=1.010"},
                AllelePair.splitLegacy("townstead_classic:elf_ears#length=1.030;townstead_classic:elf_ears#length=1.010"));
    }

    @Test
    void wildPairsSplit() {
        assertArrayEquals(new String[]{"~", "townstead_roots:gills"},
                AllelePair.splitLegacy("~;townstead_roots:gills"));
        assertArrayEquals(new String[]{"townstead_roots:gills", "~"},
                AllelePair.splitLegacy("townstead_roots:gills;~"));
        assertArrayEquals(new String[]{"~", "~"}, AllelePair.splitLegacy("~;~"));
    }

    /** The bug this codec replaces: both multi-channel alleles come back whole, tint intact. */
    @Test
    void multiChannelPairRecoversBothAllelesIntact() {
        String allele = "townstead_classic:celestial_wings#span=1.014;tint_b=0.902;tint_g=0.714;tint_r=0.949";
        assertArrayEquals(new String[]{allele, allele}, AllelePair.splitLegacy(allele + ";" + allele));
    }

    @Test
    void multiChannelPairWithWildTwinRecovers() {
        String allele = "townstead_classic:celestial_wings#span=1.014;tint_b=0.902;tint_g=0.714;tint_r=0.949";
        assertArrayEquals(new String[]{allele, "~"}, AllelePair.splitLegacy(allele + ";~"));
    }

    @Test
    void loneAlleleDoesNotSplit() {
        assertArrayEquals(new String[]{"townstead_roots:night_vision"},
                AllelePair.splitLegacy("townstead_roots:night_vision"));
        // A lone multi-channel allele's own semicolons are not pair separators.
        assertArrayEquals(new String[]{"townstead_classic:elf_ears#droop=0.400;length=1.100"},
                AllelePair.splitLegacy("townstead_classic:elf_ears#droop=0.400;length=1.100"));
    }
}
