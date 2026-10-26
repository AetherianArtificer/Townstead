package com.aetherianartificer.townstead.root.gene;

import com.aetherianartificer.townstead.TestBootstrap;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.Heredity;
import com.aetherianartificer.townstead.root.gene.types.CannibalGeneType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ties between equally strong alleles: id order without a seed, settled per individual with one. */
class SeededExpressionTest {
    @BeforeAll static void bootstrap() { TestBootstrap.ensure(); }

    private static final ResourceLocation FIRE = DataPackLang.parseId("test:fire_wings");
    private static final ResourceLocation ICE = DataPackLang.parseId("test:ice_wings");
    private static final ResourceLocation LOCUS = DataPackLang.parseId("test:wings");

    @AfterEach void clear() {
        GeneRegistry.replaceAll(Map.of(), Map.of());
    }

    private static Gene gene(ResourceLocation id) {
        var instance = new CannibalGeneType.Instance();
        return new Gene(id, Component.literal("test"), null, null, "test", Dominance.DOMINANT,
                LOCUS, 1, List.of(new GeneVariant("plain", Component.literal("plain"), 1, instance)));
    }

    private static void registry() {
        GeneRegistry.replaceAll(Map.of(FIRE, gene(FIRE), ICE, gene(ICE)), Map.of());
    }

    @Test void withoutASeedTheTieKeepsIdOrder() {
        registry();
        Allele fire = Allele.of(FIRE, "plain");
        Allele ice = Allele.of(ICE, "plain");

        assertEquals(fire, Heredity.express(fire, ice));
        assertEquals(fire, Heredity.express(ice, fire, 0L, LOCUS));
    }

    @Test void aSeedSettlesTheTieTheSameWayWhicheverCopyComesFirst() {
        registry();
        Allele fire = Allele.of(FIRE, "plain");
        Allele ice = Allele.of(ICE, "plain");
        for (long seed = 1; seed <= 200; seed++) {
            assertEquals(Heredity.express(fire, ice, seed, LOCUS), Heredity.express(ice, fire, seed, LOCUS));
        }
    }

    @Test void siblingsWithDifferentSeedsDontAllExpressTheSameAllele() {
        registry();
        Allele fire = Allele.of(FIRE, "plain");
        Allele ice = Allele.of(ICE, "plain");
        Set<ResourceLocation> winners = new HashSet<>();
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < 64; i++) winners.add(Heredity.express(fire, ice, random.nextLong() | 1L, LOCUS).geneId());

        assertTrue(winners.contains(FIRE) && winners.contains(ICE));
    }

    @Test void theSeedTravelsWithACopy() {
        Genotype genotype = new Genotype();
        genotype.set(LOCUS, Allele.of(FIRE, "plain"), Allele.of(ICE, "plain"));
        genotype.setSeed(123456789L);

        assertEquals(123456789L, genotype.copy().seed());
    }
}
