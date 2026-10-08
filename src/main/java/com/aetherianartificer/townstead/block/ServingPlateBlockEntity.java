package com.aetherianartificer.townstead.block;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.food.ServingPlateService;
import net.minecraft.core.BlockPos;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One visible dish with a server-owned portion count; deliberately not an inventory or menu. */
public final class ServingPlateBlockEntity extends BlockEntity {
    private ItemStack display = ItemStack.EMPTY;
    private ItemStack serving = ItemStack.EMPTY;

    public ServingPlateBlockEntity(BlockPos pos, BlockState state) {
        super(Townstead.SERVING_PLATE_BE.get(), pos, state);
    }

    public boolean isEmpty() { return display.isEmpty() || serving.isEmpty(); }
    public ItemStack displayStack() { return display; }
    public ItemStack servingStack() { return serving; }
    public int portions() { return serving.getCount(); }
    public int originalPortions() {
        ServingPlateService.Prepared prepared = ServingPlateService.prepare(display);
        return prepared == null ? serving.getCount() : Math.max(1, prepared.portions());
    }
    public boolean isUntouched() { return !isEmpty() && serving.getCount() == originalPortions(); }

    public boolean place(ItemStack offered) {
        if (!isEmpty() || offered.isEmpty()) return false;
        ServingPlateService.Prepared prepared = ServingPlateService.prepare(offered);
        if (prepared == null) return false;
        display = prepared.display().copyWithCount(1);
        serving = prepared.serving().copyWithCount(Math.max(1, prepared.portions()));
        changedAndSync();
        return true;
    }

    /** Commits one previously inspected serving. */
    public boolean consumeOne() {
        if (isEmpty()) return false;
        serving.shrink(1);
        if (serving.isEmpty()) clear();
        else changedAndSync();
        return true;
    }

    /** Returns the original dish when untouched, otherwise its remaining individual servings. */
    public ItemStack removeDish() {
        if (isEmpty()) return ItemStack.EMPTY;
        ItemStack result = isUntouched()
                ? display.copyWithCount(1)
                : serving.copy();
        clear();
        return result;
    }

    public void clear() {
        display = ItemStack.EMPTY;
        serving = ItemStack.EMPTY;
        changedAndSync();
    }

    private void changedAndSync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    //? if >=1.21 {
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!display.isEmpty()) tag.put("display", display.save(registries));
        if (!serving.isEmpty()) tag.put("serving", serving.save(registries));
    //?} else {
    /*protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!display.isEmpty()) tag.put("display", display.save(new CompoundTag()));
        if (!serving.isEmpty()) tag.put("serving", serving.save(new CompoundTag()));
    *///?}
    }

    @Override
    //? if >=1.21 {
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        display = tag.contains("display")
                ? ItemStack.parse(registries, tag.getCompound("display")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        serving = tag.contains("serving")
                ? ItemStack.parse(registries, tag.getCompound("serving")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    //?} else {
    /*public void load(CompoundTag tag) {
        super.load(tag);
        display = tag.contains("display") ? ItemStack.of(tag.getCompound("display")) : ItemStack.EMPTY;
        serving = tag.contains("serving") ? ItemStack.of(tag.getCompound("serving")) : ItemStack.EMPTY;
    *///?}
        // Migrate plates written by the short-lived separate portion ledger.
        int legacyPortions = Math.max(0, tag.getInt("portions"));
        if (!serving.isEmpty() && legacyPortions > serving.getCount()) {
            serving.setCount(Math.min(legacyPortions, serving.getMaxStackSize()));
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    //? if >=1.21 {
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
    //?} else {
    /*public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
    *///?}
        return tag;
    }
}
