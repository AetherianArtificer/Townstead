package com.aetherianartificer.townstead.farming;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
//? if neoforge {
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
//?} else if forge {
/*import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
*///?}
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A bucket's worth of water in any fluid container, not only the vanilla bucket. TFC disables the
 * vanilla bucket recipe; its wooden bucket carries water through the standard fluid-item handler,
 * which vanilla buckets answer too.
 */
public final class WaterContainers {
    private static final int BUCKET = 1000;

    private WaterContainers() {}

    /** Holds at least a bucket of water. */
    public static boolean isFull(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(Items.WATER_BUCKET)) return true;
        return handler(stack).map(h ->
                h.drain(water(), IFluidHandler.FluidAction.SIMULATE).getAmount() >= BUCKET).orElse(false);
    }

    /** Holds nothing and can take a whole bucket of water. */
    public static boolean isEmpty(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(Items.BUCKET)) return true;
        return handler(stack).map(h -> {
            for (int tank = 0; tank < h.getTanks(); tank++) {
                if (!h.getFluidInTank(tank).isEmpty()) return false;
            }
            return h.fill(water(), IFluidHandler.FluidAction.SIMULATE) >= BUCKET;
        }).orElse(false);
    }

    public static boolean isContainer(ItemStack stack) {
        return isFull(stack) || isEmpty(stack);
    }

    /** One container from the stack, emptied of a bucket of water; null when it holds too little. */
    @Nullable
    public static ItemStack drained(ItemStack stack) {
        if (stack.is(Items.WATER_BUCKET)) return new ItemStack(Items.BUCKET);
        ItemStack one = stack.copyWithCount(1);
        return handler(one).map(h -> {
            if (h.drain(water(), IFluidHandler.FluidAction.EXECUTE).getAmount() < BUCKET) return null;
            return h.getContainer();
        }).orElse(null);
    }

    /** One container from the stack, filled with a bucket of water; null when it cannot take it. */
    @Nullable
    public static ItemStack filled(ItemStack stack) {
        if (stack.is(Items.BUCKET)) return new ItemStack(Items.WATER_BUCKET);
        ItemStack one = stack.copyWithCount(1);
        return handler(one).map(h -> {
            if (h.fill(water(), IFluidHandler.FluidAction.EXECUTE) < BUCKET) return null;
            return h.getContainer();
        }).orElse(null);
    }

    private static FluidStack water() {
        return new FluidStack(Fluids.WATER, BUCKET);
    }

    private static Optional<IFluidHandlerItem> handler(ItemStack stack) {
        try {
            //? if neoforge {
            return FluidUtil.getFluidHandler(stack.copyWithCount(1));
            //?} else if forge {
            /*return FluidUtil.getFluidHandler(stack.copyWithCount(1)).resolve();
            *///?}
        } catch (Throwable t) {
            return Optional.empty();
        }
    }
}
