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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A lone copy shows by its gene's penetrance; two copies, or no penetrance, always show. */
class PenetranceTest {
    @BeforeAll static void bootstrap() { TestBootstrap.ensure(); }

    private static final ResourceLocation MUZZLE = DataPackLang.parseId("test:cat/muzzle");
    private static final ResourceLocation EARS = DataPackLang.parseId("test:cat/ears");
    private static final ResourceLocation ELF_EARS = DataPackLang.parseId("test:elf/ears");
    private static final ResourceLocation EARS_LOCUS = DataPackLang.parseId("test:ears");

    @AfterEach void clear() {
        GeneRegistry.replaceAll(Map.of(), Map.of());
        GenePenetrance.replaceAll(Map.of());
    }

    private static Gene gene(ResourceLocation id, ResourceLocation locus) {
        var instance = new CannibalGeneType.Instance();
        return new Gene(id, Component.literal("test"), null, null, "test", Dominance.DOMINANT,
                locus, 1, List.of(new GeneVariant("plain", Component.literal("plain"), 1, instance)));
    }

    private static void registry() {
        GeneRegistry.replaceAll(Map.of(MUZZLE, gene(MUZZLE, MUZZLE), EARS, gene(EARS, EARS_LOCUS),
                ELF_EARS, gene(ELF_EARS, EARS_LOCUS)), Map.of());
        GenePenetrance.replaceAll(Map.of(MUZZLE, 0.25f));
    }

    private static int shown(Allele a, Allele b, ResourceLocation locus) {
        int shown = 0;
        java.util.Random random = new java.util.Random(11);
        for (int i = 0; i < 2000; i++) {
            if (!Heredity.express(a, b, random.nextLong() | 1L, locus).isWild()) shown++;
        }
        return shown;
    }

    @Test void aLoneCopyShowsAboutAsOftenAsItsPenetrance() {
        registry();
        int shown = shown(Allele.of(MUZZLE, null), Allele.WILD, MUZZLE);
        assertTrue(shown > 400 && shown < 600, "shown " + shown + " of 2000");
    }

    @Test void twoCopiesAlwaysShow() {
        registry();
        assertEquals(2000, shown(Allele.of(MUZZLE, null), Allele.of(MUZZLE, null), MUZZLE));
    }

    @Test void aGeneWithoutPenetranceAlwaysShowsFromOneCopy() {
        registry();
        assertEquals(2000, shown(Allele.WILD, Allele.of(EARS, null), EARS_LOCUS));
    }

    @Test void anOldGenotypeWithoutASeedShowsEveryCopy() {
        registry();
        assertEquals(Allele.of(MUZZLE, null), Heredity.express(Allele.of(MUZZLE, null), Allele.WILD, 0L, MUZZLE));
    }

    @Test void oneIndividualAlwaysGetsTheSameAnswer() {
        registry();
        Allele muzzle = Allele.of(MUZZLE, null);
        for (long seed = 1; seed <= 200; seed++) {
            assertEquals(Heredity.express(muzzle, Allele.WILD, seed, MUZZLE), Heredity.express(Allele.WILD, muzzle, seed, MUZZLE));
        }
    }

    @Test void againstAnotherGeneALoneCopyThatFailsYields() {
        registry();
        GeneRegistry.replaceAll(Map.of(MUZZLE, gene(MUZZLE, EARS_LOCUS), EARS, gene(EARS, EARS_LOCUS),
                ELF_EARS, gene(ELF_EARS, EARS_LOCUS)), Map.of());
        GenePenetrance.replaceAll(Map.of(MUZZLE, 0.25f, ELF_EARS, 0.25f));
        // against an always-showing gene, the 0.25 copy wins only when it shows, and then half the ties
        int muzzle = 0;
        java.util.Random random = new java.util.Random(5);
        for (int i = 0; i < 2000; i++) {
            if (Heredity.express(Allele.of(MUZZLE, null), Allele.of(EARS, null), random.nextLong() | 1L, EARS_LOCUS)
                    .geneId().equals(MUZZLE)) muzzle++;
        }
        assertTrue(muzzle > 150 && muzzle < 350, "muzzle " + muzzle);
        // two weak copies of different genes still show one of them
        assertEquals(2000, shown(Allele.of(MUZZLE, null), Allele.of(ELF_EARS, null), EARS_LOCUS));
    }

    @Test void aGrantedGeneTakesItsLocusFromTheInheritedOne() {
        registry();
        List<Allele> out = Heredity.withGranted(List.of(Allele.of(EARS, null), Allele.of(MUZZLE, null)),
                List.of(Allele.of(ELF_EARS, null)));
        assertEquals(List.of(Allele.of(MUZZLE, null), Allele.of(ELF_EARS, null)), out);
    }
}
