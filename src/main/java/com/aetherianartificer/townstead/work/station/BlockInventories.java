package com.aetherianartificer.townstead.work.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if neoforge {
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
//?} else if forge {
/*import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
*///?}
import org.jetbrains.annotations.Nullable;

/** Reaching a block's item handler, on either loader's capability API. */
public final class BlockInventories {

    private BlockInventories() {}

    public static @Nullable IItemHandler itemHandler(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        return be == null ? null : itemHandler(be, level, pos, side);
    }

    public static @Nullable IItemHandler itemHandler(BlockEntity be, ServerLevel level, BlockPos pos,
                                                     @Nullable Direction side) {
        //? if neoforge {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        //?} else if forge {
        /*if (be == null) return null;
        if (side != null) return be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        *///?}
    }
}
