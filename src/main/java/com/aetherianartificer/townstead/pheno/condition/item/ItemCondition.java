package com.aetherianartificer.townstead.pheno.condition.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A predicate over an item stack with access to the world (Apoli's {@code item_condition};
 * a {@code Level} + {@code ItemStack}, like a {@code CachedItemStack}). The level is
 * present for world-dependent checks (recipes, fuel) and ignored by the rest. Used by the
 * {@code equipped_item} and {@code inventory} entity conditions.
 */
@FunctionalInterface
public interface ItemCondition {

    boolean test(Level level, ItemStack stack);

    default ItemCondition negate() {
        return (level, stack) -> !test(level, stack);
    }
}
