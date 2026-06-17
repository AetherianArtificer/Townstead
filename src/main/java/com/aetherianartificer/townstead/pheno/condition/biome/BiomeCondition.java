package com.aetherianartificer.townstead.pheno.condition.biome;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * A predicate over a biome registry entry (Apoli's {@code biome_condition}), nested in
 * the {@code biome} entity condition's {@code condition} field. The position is supplied
 * for climate checks (precipitation) that vary within a biome.
 */
@FunctionalInterface
public interface BiomeCondition {

    boolean test(Holder<Biome> biome, BlockPos pos);

    default BiomeCondition negate() {
        return (biome, pos) -> !test(biome, pos);
    }
}
