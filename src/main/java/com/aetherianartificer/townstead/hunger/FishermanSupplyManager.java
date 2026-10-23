package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.compat.starcatcher.StarcatcherCompat;
import com.aetherianartificer.townstead.profession.ProfessionSites;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import com.aetherianartificer.townstead.storage.PhysicalStorageDelivery;
import com.aetherianartificer.townstead.storage.StorageUse;
import com.aetherianartificer.townstead.storage.WorksiteStorageIndex;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Utility helpers for the Fisherman work task: rod lookup (inventory + nearby storage),
 * enchant scoring, and catch deposit helpers. Pure stateless helpers; per-task hand-visual
 * state lives on FishermanWorkTask.
 */
public final class FishermanSupplyManager {
    private FishermanSupplyManager() {}

    /**
     * True if the stack is something the fisherman will use as a fishing rod
     * for inventory pickup, hand display, and the cast animation. Matches
     * vanilla FishingRodItem plus Starcatcher rods (when that mod is loaded)
     * so villagers can wield and visibly hold modded rods without us needing
     * deep integration with Starcatcher's minigame / custom bob entity.
     */
    public static boolean isFishingRod(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof FishingRodItem) return true;
        return StarcatcherCompat.isStarcatcherRod(stack);
    }

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
            if (!isFishingRod(stack)) continue;
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
        NearbyItemSources.ContainerSlot slot = findRodInStorage(level, villager, anchor);
        if (slot == null) return false;
        ItemStack extracted = NearbyItemSources.extractOne(level, slot);
        if (extracted.isEmpty()) return false;
        ItemStack remainder = villager.getInventory().addItem(extracted);
        if (!remainder.isEmpty()) {
            // A rod is one item, so this only occurs with a truly full inventory. Return it to the
            // same source instead of dropping or duplicating it.
            NearbyItemSources.insertIntoNearbyStorage(
                    level, villager, remainder, 0, 0, slot.pos(), StorageUse.TOOL);
            if (!remainder.isEmpty()) {
                net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                        level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder.copy());
                drop.setPickUpDelay(0);
                level.addFreshEntity(drop);
            }
            return false;
        }
        WorksiteStorageIndex.invalidate(level, slot.pos());
        return true;
    }

    public static boolean rodAvailableInStorage(ServerLevel level, VillagerEntityMCA villager,
                                                BlockPos anchor) {
        return level != null && villager != null && anchor != null
                && findRodInStorage(level, villager, anchor) != null;
    }

    private static @Nullable NearbyItemSources.ContainerSlot findRodInStorage(
            ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        Set<Long> bounds = worksiteBounds(level, villager, anchor);
        return WorksiteStorageIndex.snapshot(level, villager, bounds)
                .findBestSlot(villager, FishermanSupplyManager::isFishingRod,
                        FishermanSupplyManager::scoreRod, StorageUse.TOOL);
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
        BlockPos destination = findCatchDestination(level, villager, barrelAnchor);
        return destination != null && depositCatchesAt(level, villager, destination);
    }

    public static @Nullable BlockPos findCatchDestination(
            ServerLevel level, VillagerEntityMCA villager, BlockPos barrelAnchor) {
        if (level == null || villager == null || barrelAnchor == null) return null;
        Set<Long> bounds = worksiteBounds(level, villager, barrelAnchor);
        java.util.function.Predicate<ItemStack> catches = stack -> !stack.isEmpty() && !isFishingRod(stack);
        return PhysicalStorageDelivery.findDestination(
                level, villager, bounds, catches, Set.of(), StorageUse.OUTPUT);
    }

    public static boolean catchStorageAvailable(
            ServerLevel level, VillagerEntityMCA villager, BlockPos barrelAnchor) {
        return findCatchDestination(level, villager, barrelAnchor) != null;
    }

    public static boolean depositCatchesAt(
            ServerLevel level, VillagerEntityMCA villager, BlockPos destination) {
        if (level == null || villager == null || destination == null) return false;
        java.util.function.Predicate<ItemStack> catches = stack -> !stack.isEmpty() && !isFishingRod(stack);
        return PhysicalStorageDelivery.depositMatchingAt(
                level, villager, destination, catches, StorageUse.OUTPUT) > 0;
    }

    static Set<Long> worksiteBounds(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        Set<Long> assigned = ProfessionSites.extentOf(
                level, villager, ProfessionSites.defForTask(WorkTaskTypes.FISH));
        return assigned.isEmpty() ? Set.of(anchor.asLong()) : assigned;
    }
}
