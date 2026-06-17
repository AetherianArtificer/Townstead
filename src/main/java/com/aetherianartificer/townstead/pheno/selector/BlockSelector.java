package com.aetherianartificer.townstead.pheno.selector;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Yields the block positions an {@code on} resolves to in a block action (the block analogue of
 * {@link Selector}). Fed by the same {@link com.aetherianartificer.townstead.pheno.selector.spatial.Spatial}
 * places and regions as entity selection, just extracting positions instead of entities.
 */
@FunctionalInterface
public interface BlockSelector {

    List<BlockPos> select(SelectorContext ctx);
}
