package com.aetherianartificer.townstead.root.gene;

import com.aetherianartificer.townstead.pheno.state.StateEffect;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AspectOverlayTest {
    private static final ResourceLocation EYES_LOCUS = id("test:eyes");
    private static final Map<ResourceLocation, ResourceLocation> LOCI = Map.of(
            id("test:human_eyes"), EYES_LOCUS,
            id("test:red_eyes"), EYES_LOCUS);
    private static final List<ResourceLocation> KNOWN = List.of(
            id("test:human_eyes"), id("test:red_eyes"), id("test:hunger"), id("test:sun_burn"), id("test:tall"));

    private static final AspectOverlay.GeneLookup LOOKUP = new AspectOverlay.GeneLookup() {
        @Override
        public @Nullable ResourceLocation canonical(ResourceLocation gene) {
            return KNOWN.contains(gene) ? gene : null;
        }

        @Override
        public @Nullable ResourceLocation locus(ResourceLocation canonical) {
            return LOCI.get(canonical);
        }
    };

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }

    private static StateEffect.Genes genes(List<StateEffect.Grant> grant, List<ResourceLocation> suppress) {
        return new StateEffect.Genes(grant, suppress);
    }

    private static List<String> encoded(List<Allele> alleles) {
        return alleles.stream().map(Allele::encode).toList();
    }

    private static final List<Allele> CARRIED = List.of(
            Allele.of(id("test:human_eyes"), "brown"), Allele.of(id("test:hunger"), null), Allele.of(id("test:tall"), null));

    @Test
    void grantReplacesTheCarriedAlleleAtItsLocus() {
        List<Allele> out = AspectOverlay.apply(CARRIED,
                List.of(genes(List.of(new StateEffect.Grant(id("test:red_eyes"), "crimson")), List.of())), LOOKUP, 42L);
        assertEquals(List.of("test:hunger", "test:tall", "test:red_eyes#crimson"), encoded(out));
    }

    @Test
    void grantWithoutLocusIsAdded() {
        List<Allele> out = AspectOverlay.apply(CARRIED,
                List.of(genes(List.of(new StateEffect.Grant(id("test:sun_burn"), null)), List.of())), LOOKUP, 42L);
        assertEquals(List.of("test:human_eyes#brown", "test:hunger", "test:tall", "test:sun_burn"), encoded(out));
    }

    @Test
    void suppressRemovesAndGrantWinsTheSameGene() {
        List<Allele> suppressed = AspectOverlay.apply(CARRIED,
                List.of(genes(List.of(), List.of(id("test:hunger")))), LOOKUP, 42L);
        assertEquals(List.of("test:human_eyes#brown", "test:tall"), encoded(suppressed));

        List<Allele> regranted = AspectOverlay.apply(CARRIED, List.of(
                genes(List.of(new StateEffect.Grant(id("test:tall"), null)), List.of()),
                genes(List.of(), List.of(id("test:tall")))), LOOKUP, 42L);
        assertEquals(List.of("test:human_eyes#brown", "test:hunger", "test:tall"), encoded(regranted));
    }

    @Test
    void laterOverlayWinsAContestedLocus() {
        List<Allele> out = AspectOverlay.apply(CARRIED, List.of(
                genes(List.of(new StateEffect.Grant(id("test:red_eyes"), "crimson")), List.of()),
                genes(List.of(new StateEffect.Grant(id("test:human_eyes"), "gold")), List.of())), LOOKUP, 42L);
        assertEquals(List.of("test:hunger", "test:tall", "test:human_eyes#gold"), encoded(out));
    }

    @Test
    void unknownGenesAreIgnored() {
        List<Allele> out = AspectOverlay.apply(CARRIED, List.of(
                genes(List.of(new StateEffect.Grant(id("absent:fangs"), null)), List.of(id("absent:thing")))), LOOKUP, 42L);
        assertEquals(encoded(CARRIED), encoded(out));
    }

    @Test
    void unnamedVariantsRollPerCarrierAndStayStable() {
        AspectOverlay.GeneLookup rolling = new AspectOverlay.GeneLookup() {
            @Override public @Nullable ResourceLocation canonical(ResourceLocation gene) { return LOOKUP.canonical(gene); }
            @Override public @Nullable ResourceLocation locus(ResourceLocation canonical) { return LOOKUP.locus(canonical); }
            @Override public @Nullable String roll(ResourceLocation canonical, long seed) { return "v" + Math.floorMod(seed, 17L); }
        };
        List<StateEffect.Genes> grant = List.of(genes(List.of(new StateEffect.Grant(id("test:red_eyes"), null)), List.of()));
        String first = encoded(AspectOverlay.apply(CARRIED, grant, rolling, 1L)).get(2);
        assertEquals(first, encoded(AspectOverlay.apply(CARRIED, grant, rolling, 1L)).get(2));
        boolean varies = false;
        for (long seed = 2; seed < 40 && !varies; seed++) {
            varies = !first.equals(encoded(AspectOverlay.apply(CARRIED, grant, rolling, seed)).get(2));
        }
        assertEquals(true, varies);
        String named = encoded(AspectOverlay.apply(CARRIED,
                List.of(genes(List.of(new StateEffect.Grant(id("test:red_eyes"), "crimson")), List.of())), rolling, 1L)).get(2);
        assertEquals("test:red_eyes#crimson", named);
    }

    @Test
    void noOverlayLeavesTheExpressionUntouched() {
        assertEquals(encoded(CARRIED), encoded(AspectOverlay.apply(CARRIED, List.of(), LOOKUP, 42L)));
    }
}
