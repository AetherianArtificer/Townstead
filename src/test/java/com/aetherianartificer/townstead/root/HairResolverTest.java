package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.appearance.HairPolicy;
import com.aetherianartificer.townstead.root.appearance.HairPolicyRegistry;
import com.aetherianartificer.townstead.root.appearance.HairResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HairResolverTest {

    private static final ResourceLocation SPECIES = id("humanoid");
    private static final ResourceLocation ANCESTRY = id("elf");
    private static final ResourceLocation LINEAGE = id("moon_elf");
    private static final ResourceLocation ROOT = id("moon_elf_root");
    private static final ResourceLocation HERITAGE = id("pure_elf");

    @Test
    void closestAuthoredIdentityLevelWins() {
        SpeciesRegistry.replaceAll(Map.of(SPECIES, new Species(SPECIES, Component.literal("Humanoid"),
                Rig.VILLAGER, Animations.DEFAULT, true, 0f, Genome.EMPTY, null)));
        AncestryRegistry.replaceAll(Map.of(ANCESTRY, new Ancestry(ANCESTRY, Component.literal("Elf"),
                SPECIES, null, null, Genome.EMPTY, SpawnBias.EMPTY, null)));
        LineageRegistry.replaceAll(Map.of(LINEAGE, new Lineage(LINEAGE, Component.literal("Moon Elf"),
                ANCESTRY, null, null, Genome.EMPTY, SpawnBias.EMPTY, null)));
        RootRegistry.replaceAll(Map.of(ROOT, new Root(ROOT, Component.literal("Moon Elf"), null, null,
                LINEAGE, null, null, Genome.EMPTY, SpawnBias.EMPTY, null, null, false)));
        HeritageProfile profile = new HeritageProfile(HERITAGE, Component.literal("Pure Elf"), null, null,
                10, Map.of(ANCESTRY, new GeneRange(1f, 1f)));
        HeritageRegistry.replaceAll(Map.of(HERITAGE, profile));
        Heritage realized = Heritage.pure(ANCESTRY);

        assertTrue(HairResolver.enabled(ROOT, realized));

        HairPolicyRegistry.setSpecies(Map.of(SPECIES, new HairPolicy(false)));
        assertFalse(HairResolver.enabled(ROOT, realized));

        HairPolicyRegistry.setAncestry(Map.of(ANCESTRY, new HairPolicy(true)));
        assertTrue(HairResolver.enabled(ROOT, realized));

        HairPolicyRegistry.setLineage(Map.of(LINEAGE, new HairPolicy(false)));
        assertFalse(HairResolver.enabled(ROOT, realized));

        HairPolicyRegistry.setHeritage(Map.of(HERITAGE, new HairPolicy(true)));
        assertTrue(HairResolver.enabled(ROOT, realized));
    }

    @Test
    void colorRangesResolveIndependentlyFromEnabled() {
        SpeciesRegistry.replaceAll(Map.of(SPECIES, new Species(SPECIES, Component.literal("Humanoid"),
                Rig.VILLAGER, Animations.DEFAULT, true, 0f, Genome.EMPTY, null)));
        AncestryRegistry.replaceAll(Map.of(ANCESTRY, new Ancestry(ANCESTRY, Component.literal("Elf"),
                SPECIES, null, null, Genome.EMPTY, SpawnBias.EMPTY, null)));
        LineageRegistry.replaceAll(Map.of(LINEAGE, new Lineage(LINEAGE, Component.literal("Moon Elf"),
                ANCESTRY, null, null, Genome.EMPTY, SpawnBias.EMPTY, null)));
        RootRegistry.replaceAll(Map.of(ROOT, new Root(ROOT, Component.literal("Moon Elf"), null, null,
                LINEAGE, null, null, Genome.EMPTY, SpawnBias.EMPTY, null, null, false)));
        var pale = new com.aetherianartificer.townstead.root.appearance.HairColorRange(
                new GeneRange(0f, 0.15f), new GeneRange(0f, 0.1f), 1);
        var blue = new com.aetherianartificer.townstead.root.appearance.HairColorChoice(0x4A78FF, 1);
        HairPolicyRegistry.setSpecies(Map.of(SPECIES, new HairPolicy(false, List.of(pale))));
        HairPolicyRegistry.setAncestry(Map.of(ANCESTRY,
                new HairPolicy(null, List.of(), List.of(blue))));
        HairPolicyRegistry.setLineage(Map.of(LINEAGE, new HairPolicy(true)));

        var resolved = HairResolver.resolve(ROOT, Heritage.pure(ANCESTRY));
        assertTrue(resolved.enabled());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(pale), resolved.colorRanges());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(blue), resolved.colors());
    }

    @AfterEach
    void clearRegistries() {
        SpeciesRegistry.replaceAll(Map.of());
        AncestryRegistry.replaceAll(Map.of());
        LineageRegistry.replaceAll(Map.of());
        RootRegistry.replaceAll(Map.of());
        HeritageRegistry.replaceAll(Map.of());
        HairPolicyRegistry.setSpecies(Map.of());
        HairPolicyRegistry.setAncestry(Map.of());
        HairPolicyRegistry.setLineage(Map.of());
        HairPolicyRegistry.setHeritage(Map.of());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.tryParse("test:" + path);
    }
}
