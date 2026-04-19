package com.aetherianartificer.townstead.hunger;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import javax.annotation.Nullable;

/**
 * Utility helpers for the Fisherman work task: rod lookup (inventory + nearby storage),
 * enchant scoring, and catch deposit helpers. Pure stateless helpers; per-task hand-visual
 * state lives on FishermanWorkTask.
 */
public final class FishermanSupplyManager {
    private FishermanSupplyManager() {}

    /**
     * Return the villager's best fishing rod from their inventory, or null if none.
     * Returns the live stack reference so the caller can mutate durability directly.
     * Preference order: highest total enchantment level, then remaining durability.
     */
    public static @Nullable ItemStack findRodInInventory(SimpleContainer inv) {
        if (inv == null) return null;
        ItemStack best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof FishingRodItem)) continue;
            int score = scoreRod(stack);
            if (score > bestScore) {
                bestScore = score;
                best = stack;
            }
        }
        return best;
    }

    /**
     * Pull a fishing rod from nearby storage into the villager's inventory, centered on the
     * barrel anchor. Returns true on success.
     */
    public static boolean pullRodFromStorage(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                                             int horizontalRadius, int verticalRadius) {
        if (level == null || villager == null || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level,
                villager,
                horizontalRadius,
                verticalRadius,
                stack -> stack.getItem() instanceof FishingRodItem,
                FishermanSupplyManager::scoreRod,
                anchor
        );
    }

    /**
     * Score a rod for selection: total enchantment level dominates (x1000) with remaining
     * durability as a tiebreaker. Higher is better.
     */
    public static int scoreRod(ItemStack stack) {
        if (stack.isEmpty()) return Integer.MIN_VALUE;
        int enchantSum = totalEnchantLevel(stack);
        int remainingDurability = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        return enchantSum * 1000 + remainingDurability;
    }

    /** Sum of all enchantment levels on a stack, safe across 1.20.1/1.21.1. */
    public static int totalEnchantLevel(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int total = 0;
        //? if >=1.21 {
        var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (var entry : enchantments.entrySet()) {
            total += entry.getIntValue();
        }
        //?} else {
        /*var enchantments = EnchantmentHelper.getEnchantments(stack);
        for (Integer lvl : enchantments.values()) {
            if (lvl != null) total += lvl;
        }
        *///?}
        return total;
    }

    /**
     * Deposit non-rod items from the villager's inventory into nearby storage, preferring the
     * barrel at barrelAnchor (first container scanned from that center wins).
     * Returns true if at least one item moved.
     */
    public static boolean depositCatches(ServerLevel level, VillagerEntityMCA villager, BlockPos barrelAnchor,
                                         int horizontalRadius, int verticalRadius) {
        if (level == null || villager == null || barrelAnchor == null) return false;
        SimpleContainer inv = villager.getInventory();
        boolean movedAny = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof FishingRodItem) continue;

            int before = stack.getCount();
            ItemStack working = stack;
            NearbyItemSources.insertIntoNearbyStorage(level, villager, working, horizontalRadius, verticalRadius, barrelAnchor);
            if (working.getCount() != before) {
                movedAny = true;
                if (working.isEmpty()) {
                    inv.setItem(i, ItemStack.EMPTY);
                } else {
                    inv.setItem(i, working);
                }
            }
        }
        if (movedAny) inv.setChanged();
        return movedAny;
    }
}
