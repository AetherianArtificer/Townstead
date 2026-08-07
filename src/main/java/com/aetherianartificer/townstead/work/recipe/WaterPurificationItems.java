package com.aetherianartificer.townstead.work.recipe;

import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.work.station.StationInventoryOps;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Pure selection/accounting rules for optional water-purification production recipes. */
public final class WaterPurificationItems {
    private WaterPurificationItems() {}

    public static int impurityScore(ItemStack stack, ThirstCompatBridge bridge) {
        if (stack.isEmpty() || !bridge.itemRestoresThirst(stack) || !bridge.isDrink(stack)
                || !bridge.isPurityWaterContainer(stack)) return 0;
        int purity = Math.max(0, Math.min(ThirstCompatBridge.PURITY_PURIFIED, bridge.purity(stack)));
        if (purity >= ThirstCompatBridge.PURITY_PURIFIED) return 0;
        return ((ThirstCompatBridge.PURITY_PURIFIED - purity) * 100)
                + Math.max(0, bridge.hydration(stack));
    }

    public static int bestSlot(SimpleContainer inventory, ThirstCompatBridge bridge,
                               Predicate<ItemStack> extraFilter) {
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!extraFilter.test(stack)) continue;
            int score = impurityScore(stack, bridge);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestScore > 0 ? bestSlot : -1;
    }

    public static int countMatching(SimpleContainer inventory, ItemStack prototype,
                                    ThirstCompatBridge bridge) {
        if (prototype.isEmpty()) return 0;
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !StationInventoryOps.sameItemAndComponents(stack, prototype)) continue;
            if (impurityScore(stack, bridge) > 0) total += stack.getCount();
        }
        return total;
    }

    public static int consumeMatching(SimpleContainer inventory, ItemStack prototype,
                                      ThirstCompatBridge bridge, int amount) {
        if (prototype.isEmpty() || amount <= 0) return 0;
        int consumed = 0;
        for (int slot = 0; slot < inventory.getContainerSize() && consumed < amount; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !StationInventoryOps.sameItemAndComponents(stack, prototype)) continue;
            if (impurityScore(stack, bridge) <= 0) continue;
            int move = Math.min(amount - consumed, stack.getCount());
            stack.shrink(move);
            consumed += move;
        }
        return consumed;
    }
}
