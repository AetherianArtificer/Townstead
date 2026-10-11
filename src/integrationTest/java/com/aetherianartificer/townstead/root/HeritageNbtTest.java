package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.gene.Allele;
import com.aetherianartificer.townstead.root.gene.Genotype;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lineage shares and the expression seed survive a save, and older saves read without them. */
class HeritageNbtTest {

    private static final ResourceLocation DRACONIC = ResourceLocation.tryParse("test:draconic");
    private static final ResourceLocation FIRE = ResourceLocation.tryParse("test:fire_drake");
    private static final ResourceLocation ICE = ResourceLocation.tryParse("test:ice_drake");

    @Test
    void lineageSharesRoundTrip() {
        Heritage steam = Heritage.blend(Heritage.pure(DRACONIC, FIRE), Heritage.pure(DRACONIC, ICE));
        Heritage read = Heritage.fromTag(steam.toTag());

        assertEquals(1f, read.fractionOf(DRACONIC), 1e-4);
        assertEquals(0.5f, read.lineageFractionOf(FIRE), 1e-4);
        assertEquals(0.5f, read.lineageFractionOf(ICE), 1e-4);
    }

    @Test
    void anOlderHeritageReadsAsAncestryOnly() {
        CompoundTag old = new CompoundTag();
        old.putFloat(DRACONIC.toString(), 1f);
        Heritage read = Heritage.fromTag(old);

        assertEquals(1f, read.fractionOf(DRACONIC), 1e-4);
        assertTrue(read.lineages().isEmpty());
    }

    @Test
    void theExpressionSeedRoundTrips() {
        Genotype genotype = new Genotype();
        genotype.set(ResourceLocation.tryParse("test:wings"), Allele.of(FIRE, "plain"), Allele.of(ICE, "plain"));
        genotype.setSeed(123456789L);

        Genotype read = Genotype.fromTag(genotype.toTag());
        assertEquals(123456789L, read.seed());
        assertEquals(1, read.loci().size());
        assertEquals(0L, Genotype.fromTag(new Genotype().toTag()).seed());
    }
}
