package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.needs.Consumables;
import com.aetherianartificer.townstead.needs.NeedEffectProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Adds Townstead datapack consumables to a thirst mod's native drink vocabulary. */
final class ConfiguredThirstBridge implements ThirstCompatBridge {
    private final ThirstCompatBridge delegate;

    ConfiguredThirstBridge(ThirstCompatBridge delegate) { this.delegate = delegate; }

    private NeedEffectProjection configured(ItemStack stack) {
        Consumables.Definition definition = Consumables.resolve(stack);
        if (definition == null) return NeedEffectProjection.NONE;
        if (definition.fallback() && delegate.itemRestoresThirst(stack)) return NeedEffectProjection.NONE;
        return definition.projection();
    }

    @Override public boolean isActive() { return delegate.isActive(); }
    @Override public boolean itemRestoresThirst(ItemStack stack) {
        return configured(stack).hydrates() || delegate.itemRestoresThirst(stack);
    }
    @Override public boolean isDrink(ItemStack stack) {
        return configured(stack).hydrates() || delegate.isDrink(stack);
    }
    @Override public boolean isPurityWaterContainer(ItemStack stack) { return delegate.isPurityWaterContainer(stack); }
    @Override public int hydration(ItemStack stack) {
        NeedEffectProjection effect = configured(stack);
        return effect.hydrates() ? effect.immediateHydration() : delegate.hydration(stack);
    }
    @Override public int quenched(ItemStack stack) {
        NeedEffectProjection effect = configured(stack);
        return effect.hydrates() ? effect.lastingHydration() : delegate.quenched(stack);
    }
    @Override public int purity(ItemStack stack) { return delegate.purity(stack); }
    @Override public float exhaustionBiomeModifier(Level level, BlockPos pos) { return delegate.exhaustionBiomeModifier(level, pos); }
    @Override public boolean extraHydrationToQuenched() { return delegate.extraHydrationToQuenched(); }
    @Override public PurityResult evaluatePurity(int purity, RandomSource random) { return delegate.evaluatePurity(purity, random); }
    @Override public ResourceLocation iconTexture() { return delegate.iconTexture(); }
    @Override public boolean supportsPurification() { return delegate.supportsPurification(); }
    @Override public ResourceLocation purificationOutput() { return delegate.purificationOutput(); }
    @Override public void purifyResult(ItemStack input, ItemStack output) { delegate.purifyResult(input, output); }
    @Override public ItemStack onDrinkConsumed(ItemStack stack) { return delegate.onDrinkConsumed(stack); }
    @Override public ThirstIconInfo iconInfo(int thirst) { return delegate.iconInfo(thirst); }
    @Override public double playerThirst(Player player) { return delegate.playerThirst(player); }
}
