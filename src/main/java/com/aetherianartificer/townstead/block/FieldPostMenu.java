package com.aetherianartificer.townstead.block;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
//? if >=1.21 {
import net.minecraft.network.RegistryFriendlyByteBuf;
//?}
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Container menu for the Field Post. No inventory slots - pure config UI.
 */
public class FieldPostMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    @Nullable private final FieldPostBlockEntity blockEntity;

    // Server-side constructor
    public FieldPostMenu(int containerId, Inventory playerInventory, FieldPostBlockEntity blockEntity) {
        super(Townstead.FIELD_POST_MENU.get(), containerId);
        this.pos = blockEntity.getBlockPos();
        this.blockEntity = blockEntity;
    }

    // Client-side constructor (from network)
    //? if >=1.21 {
    public static FieldPostMenu clientFactory(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
    //?} else {
    /*public static FieldPostMenu clientFactory(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
    *///?}
        BlockPos pos = buf.readBlockPos();
        Level level = playerInventory.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FieldPostBlockEntity fieldPost) {
            return new FieldPostMenu(containerId, playerInventory, fieldPost);
        }
        // Fallback: create a menu without block entity reference (client-only, data comes via sync packet)
        return new FieldPostMenu(containerId, playerInventory, pos);
    }

    // Client fallback constructor
    private FieldPostMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(Townstead.FIELD_POST_MENU.get(), containerId);
        this.pos = pos;
        this.blockEntity = null;
    }

    public BlockPos getPos() {
        return pos;
    }

    @Nullable
    public FieldPostBlockEntity getBlockEntity() {
        return blockEntity;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
