package com.aetherianartificer.townstead.pheno.selector;

import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Yields the targets an {@code on} resolves to (Pheno's unified selection). A role yields zero or
 * one, a query like {@code nearby} yields many, an array is their union. Consumers are uniform: an
 * action with {@code on} runs once per yielded target, {@code count} aggregates them to a number,
 * and {@code any}/{@code all}/{@code none} test them.
 */
@FunctionalInterface
public interface Selector {

    List<LivingEntity> select(SelectorContext ctx);
}
