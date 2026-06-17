package com.aetherianartificer.townstead.pheno.condition.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * A predicate over a block position (Apoli's {@code block_condition}; a level + pos,
 * like a {@code CachedBlockPosition}). Used by the {@code block} entity condition and
 * the block {@code area_of_effect} filter.
 */
@FunctionalInterface
public interface BlockCondition {

    boolean test(Level level, BlockPos pos);

    default BlockCondition negate() {
        return (level, pos) -> !test(level, pos);
    }
}
