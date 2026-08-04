package com.aetherianartificer.townstead.work.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if neoforge {
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
//?} else if forge {
/*import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
*///?}
import org.jetbrains.annotations.Nullable;

/**
 * Reaching a block's inventory, on either loader's capability API.
 *
 * <p>The capability is asked first, then the plain {@link Container} interface. That fallback is
 * not a nicety: a mod only exposes an item-handler capability if it registers one, and a
 * multi-loader mod written against vanilla interfaces usually does not. Farm &amp; Charm is the
 * case in point — its stove and cooking pot are {@link WorldlyContainer}s with no capability, so
 * asking only the capability returned nothing, every one of its stations reported FOREIGN, and
 * {@code classify} turned that into BLOCKED. The cook stood in a fully stocked kitchen and could
 * not see inside a single machine.</p>
 */
public final class BlockInventories {

    private BlockInventories() {}

    public static @Nullable IItemHandler itemHandler(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        return be == null ? null : itemHandler(be, level, pos, side);
    }

    public static @Nullable IItemHandler itemHandler(BlockEntity be, ServerLevel level, BlockPos pos,
                                                     @Nullable Direction side) {
        //? if neoforge {
        IItemHandler capability = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        //?} else if forge {
        /*if (be == null) return null;
        IItemHandler capability = side != null
                ? be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null)
                : be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        *///?}
        if (capability != null) return capability;
        return wrapContainer(be, side);
    }

    /**
     * Wraps a vanilla container as an item handler. A sided container is wrapped side-aware when
     * a face is asked for, so its own declaration of which slots that face reaches is honoured;
     * asking for no side deliberately gets every slot, which is what counting wants.
     */
    private static @Nullable IItemHandler wrapContainer(@Nullable BlockEntity be, @Nullable Direction side) {
        if (be instanceof WorldlyContainer sided && side != null) return new SidedInvWrapper(sided, side);
        if (be instanceof Container container) return new InvWrapper(container);
        return null;
    }
}
