package com.aetherianartificer.townstead.compat.thirst;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface ThirstCompatBridge {
    boolean isActive();
    boolean itemRestoresThirst(ItemStack stack);
    boolean isDrink(ItemStack stack);
    boolean isPurityWaterContainer(ItemStack stack);
    int hydration(ItemStack stack);
    int quenched(ItemStack stack);
    int purity(ItemStack stack);
    float exhaustionBiomeModifier(Level level, BlockPos pos);
    boolean extraHydrationToQuenched();
    PurityResult evaluatePurity(int purity, RandomSource random);
    ResourceLocation iconTexture();

    boolean supportsPurification();

    default void purifyResult(ItemStack input, ItemStack output) {}

    ThirstIconInfo iconInfo(int thirst);

    record PurityResult(boolean applyHydration, boolean sickness, boolean poison, int purity) {}

    record ThirstIconInfo(ResourceLocation texture, int u, int v, int texW, int texH) {}
}
