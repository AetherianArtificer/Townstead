package com.aetherianartificer.townstead.hunger.diet;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Servings of fluid drawn from a container item (a bottle, a bucket, a modded flask) through its fluid handler. */
public final class ItemFluids {
    private ItemFluids() {}

    /** Whether {@code fluid} names this fluid: an id, or a {@code #tag} when {@code tag} is set. */
    public static boolean is(Fluid fluid, ResourceLocation id, boolean tag) {
        return tag ? fluid.builtInRegistryHolder().is(TagKey.create(Registries.FLUID, id))
                : id.equals(BuiltInRegistries.FLUID.getKey(fluid));
    }

    /** Whether one of {@code stack} holds at least {@code amount} of the named fluid. */
    public static boolean holds(ItemStack stack, ResourceLocation id, boolean tag, int amount) {
        return drain(stack, id, tag, amount, true) != null;
    }

    /**
     * Draws {@code amount} of the named fluid from one of {@code stack}, leaving {@code stack} itself
     * untouched. Returns the container as it is afterwards, or null when it cannot give that much.
     * With {@code simulate}, nothing is drawn and the returned container is unchanged.
     */
    public static @Nullable ItemStack drain(ItemStack stack, ResourceLocation id, boolean tag, int amount, boolean simulate) {
        if (stack.isEmpty() || amount <= 0) return null;
        ItemStack one = stack.copyWithCount(1);
        //? if neoforge {
        Optional<net.neoforged.neoforge.fluids.capability.IFluidHandlerItem> handler =
                net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(one);
        if (handler.isEmpty()) return null;
        var fluids = handler.get();
        var probe = fluids.drain(amount, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
        if (probe.isEmpty() || probe.getAmount() < amount || !is(probe.getFluid(), id, tag)) return null;
        if (simulate) return one;
        fluids.drain(amount, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        return fluids.getContainer();
        //?} else {
        /*Optional<net.minecraftforge.fluids.capability.IFluidHandlerItem> handler =
                net.minecraftforge.fluids.FluidUtil.getFluidHandler(one).resolve();
        if (handler.isEmpty()) return null;
        var fluids = handler.get();
        var probe = fluids.drain(amount, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
        if (probe.isEmpty() || probe.getAmount() < amount || !is(probe.getFluid(), id, tag)) return null;
        if (simulate) return one;
        fluids.drain(amount, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        return fluids.getContainer();
        *///?}
    }
}
