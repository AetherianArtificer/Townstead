package com.aetherianartificer.townstead.root.gene;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.Heredity;
import com.aetherianartificer.townstead.root.gene.types.CannibalGeneType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneRegistryNestedPathTest {

    private static ResourceLocation id(String value) {
        return DataPackLang.parseId(value);
    }

    @AfterEach
    void clearRegistry() {
        GeneRegistry.replaceAll(Map.of(), Map.of());
    }

    @Test
    void nestedGeneGetsAnUnambiguousFlatCompatibilityAlias() {
        ResourceLocation nested = id("example:elf/night_vision");

        GeneRegistry.AliasIndex index = GeneRegistry.buildAliases(Set.of(nested), Set.of());

        assertEquals(nested, index.aliases().get(id("example:night_vision")));
        assertTrue(index.ambiguous().isEmpty());
    }

    @Test
    void duplicateBasenamesRequireFolderQualifiedIds() {
        ResourceLocation elf = id("example:elf/night_vision");
        ResourceLocation orc = id("example:orc/night_vision");

        GeneRegistry.AliasIndex index = GeneRegistry.buildAliases(Set.of(elf, orc), Set.of());

        assertFalse(index.aliases().containsKey(id("example:night_vision")));
        assertTrue(index.ambiguous().contains(id("example:night_vision")));
    }

    @Test
    void realFlatGeneWinsOverNestedCompatibilityAlias() {
        ResourceLocation flat = id("example:night_vision");
        ResourceLocation nested = id("example:elf/night_vision");

        GeneRegistry.AliasIndex index = GeneRegistry.buildAliases(Set.of(flat, nested), Set.of());

        assertFalse(index.aliases().containsKey(flat));
        assertTrue(index.ambiguous().isEmpty());
    }

    @Test
    void companionGenesDoNotClaimTheirOwnBasenameAliases() {
        ResourceLocation parent = id("example:elf/triple_jump");
        ResourceLocation companion = id("example:elf/triple_jump/jumps");

        GeneRegistry.AliasIndex index = GeneRegistry.buildAliases(
                Set.of(parent, companion), Set.of(companion));

        assertEquals(parent, index.aliases().get(id("example:triple_jump")));
        assertFalse(index.aliases().containsKey(id("example:jumps")));
    }

    @Test
    void movedFlatGeneResolvesAndMigratesSavedPrivateLocus() {
        ResourceLocation oldFlatId = id("example:night_vision");
        ResourceLocation nestedId = id("example:elf/night_vision");
        Gene nested = gene(nestedId);
        GeneRegistry.replaceAll(Map.of(nestedId, nested), Map.of());

        assertSame(nested, GeneRegistry.byId(oldFlatId));
        assertEquals(nestedId, GeneRegistry.canonicalId(oldFlatId));

        Genotype saved = new Genotype();
        Allele oldAllele = Allele.of(oldFlatId, null);
        saved.set(oldFlatId, oldAllele, oldAllele);

        assertTrue(Heredity.canonicalizeLoci(saved));
        assertFalse(saved.has(oldFlatId));
        Allele[] migrated = saved.at(nestedId);
        assertEquals(nestedId, migrated[0].geneId());
        assertEquals(nestedId, migrated[1].geneId());
    }

    @Test
    void movedFlatGeneCanonicalizesAllelesAtASharedLocus() {
        ResourceLocation oldFlatId = id("example:night_vision");
        ResourceLocation nestedId = id("example:elf/night_vision");
        ResourceLocation sharedLocus = id("example:vision");
        Gene nested = gene(nestedId, sharedLocus);
        GeneRegistry.replaceAll(Map.of(nestedId, nested), Map.of());

        Genotype saved = new Genotype();
        Allele oldAllele = Allele.of(oldFlatId, null);
        saved.set(sharedLocus, oldAllele, oldAllele);

        assertTrue(Heredity.canonicalizeLoci(saved));
        Allele[] migrated = saved.at(sharedLocus);
        assertEquals(nestedId, migrated[0].geneId());
        assertEquals(nestedId, migrated[1].geneId());
    }

    private static Gene gene(ResourceLocation id) {
        return gene(id, null);
    }

    private static Gene gene(ResourceLocation id, ResourceLocation locus) {
        CannibalGeneType.Instance instance = new CannibalGeneType.Instance();
        return new Gene(id, Component.literal("test"), null, null, "test",
                Dominance.RECESSIVE, locus, 1,
                List.of(new GeneVariant(id.getPath(), Component.literal("test"), 1, instance)));
    }
}
