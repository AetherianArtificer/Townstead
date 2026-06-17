package com.aetherianartificer.townstead.pheno.selector;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Yields the live item stacks an {@code on} resolves to in an item action (the item analogue of
 * {@link Selector}). Item sources are non-spatial: equipment slots and inventory. The stacks are
 * the holder's actual stacks, so an item action mutating one is reflected in the slot.
 */
@FunctionalInterface
public interface ItemSelector {

    List<ItemStack> select(LivingEntity holder);
}
