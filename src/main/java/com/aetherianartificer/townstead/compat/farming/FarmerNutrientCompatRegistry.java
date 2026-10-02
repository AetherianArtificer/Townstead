package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class FarmerNutrientCompatRegistry {
    private static final List<FarmerNutrientCompat> PROVIDERS = List.of(
            new TfcNutrientCompat()
    );

    private FarmerNutrientCompatRegistry() {}

    public static boolean available() {
        for (FarmerNutrientCompat provider : PROVIDERS) {
            if (ModCompat.isLoaded(provider.modId())) return true;
        }
        return false;
    }

    public static boolean wantsFeeding(ServerLevel level, BlockPos soilPos) {
        for (FarmerNutrientCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.wantsFeeding(level, soilPos)) return true;
        }
        return false;
    }

    public static boolean isFertilizer(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (FarmerNutrientCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.isFertilizer(stack)) return true;
        }
        return false;
    }

    public static boolean wouldHelp(ServerLevel level, BlockPos soilPos, ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (FarmerNutrientCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.wouldHelp(level, soilPos, stack)) return true;
        }
        return false;
    }

    public static boolean feed(ServerLevel level, BlockPos soilPos, ItemStack stack) {
        for (FarmerNutrientCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.wouldHelp(level, soilPos, stack)) return provider.feed(level, soilPos, stack);
        }
        return false;
    }

    @Nullable
    public static Item icon() {
        for (FarmerNutrientCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            Item icon = provider.icon();
            if (icon != null) return icon;
        }
        return null;
    }
}
