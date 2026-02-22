package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class FarmerHarvestToolCompatRegistry {
    private static final List<FarmerHarvestToolCompat> PROVIDERS = List.of(
            new FarmersDelightKnifeCompat()
    );

    private FarmerHarvestToolCompatRegistry() {}

    public static boolean hasAnyLoadedProvider() {
        for (FarmerHarvestToolCompat provider : PROVIDERS) {
            if (ModCompat.isLoaded(provider.modId())) return true;
        }
        return false;
    }

    public static boolean isCompatibleTool(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (FarmerHarvestToolCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.matches(stack)) return true;
        }
        return false;
    }

    public static boolean shouldUseToolForBlock(BlockState state) {
        for (FarmerHarvestToolCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.shouldUseForBlock(state)) return true;
        }
        return false;
    }

    public static int findPreferredToolSlot(SimpleContainer inv) {
        if (!hasAnyLoadedProvider()) return -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isCompatibleTool(inv.getItem(i))) return i;
        }
        return -1;
    }

    public static int findPreferredToolSlotForBlock(SimpleContainer inv, BlockState state) {
        if (!hasAnyLoadedProvider()) return -1;
        for (FarmerHarvestToolCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (!provider.shouldUseForBlock(state)) continue;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (provider.matches(inv.getItem(i))) return i;
            }
        }
        return -1;
    }

    public static ItemStack getPreferredTool(SimpleContainer inv) {
        int slot = findPreferredToolSlot(inv);
        if (slot < 0) return ItemStack.EMPTY;
        return inv.getItem(slot);
    }

    public static ItemStack getPreferredToolForBlock(SimpleContainer inv, BlockState state) {
        int slot = findPreferredToolSlotForBlock(inv, state);
        if (slot < 0) return ItemStack.EMPTY;
        return inv.getItem(slot);
    }
}
