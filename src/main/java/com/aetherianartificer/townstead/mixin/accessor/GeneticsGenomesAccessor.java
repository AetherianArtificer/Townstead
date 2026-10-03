package com.aetherianartificer.townstead.mixin.accessor;

import net.conczin.mca.entity.ai.Genetics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/** Every registered MCA gene type, for McaGeneticsCompat's client-side roll. */
@Mixin(value = Genetics.class, remap = false)
public interface GeneticsGenomesAccessor {
    @Accessor("GENOMES")
    static Set<Genetics.GeneType> townstead$getGenomes() {
        throw new AssertionError();
    }
}
