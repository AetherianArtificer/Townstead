package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.aetherianartificer.townstead.work.station.BlockInventories;
import com.google.gson.JsonObject;
import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
//? if neoforge {
import net.neoforged.neoforge.items.IItemHandler;
//?} else {
/*import net.minecraftforge.items.IItemHandler;
*///?}

/** Bounded restocking through a block's public inventory, using the supplied item role. */
public final class InsertItemBlockActionType implements BlockActionType {
    @Override public String key() { return "pheno:insert_item"; }

    @Override public BlockAction parse(JsonObject json) {
        int slot = GsonHelper.getAsInt(json, "slot", -1);
        int count = GsonHelper.getAsInt(json, "count", 1);
        int limit = GsonHelper.getAsInt(json, "limit", 1);
        int inventoryLimit = GsonHelper.getAsInt(json, "inventory_limit", Integer.MAX_VALUE);
        String role = GsonHelper.getAsString(json, "item", "item");
        String rawSide = GsonHelper.getAsString(json, "side", "up");
        Direction side = Direction.byName(rawSide);
        boolean rejectRemainders = GsonHelper.getAsBoolean(json, "reject_remainders", false);
        if (slot < 0 || count < 1 || limit < 1 || inventoryLimit < 1 || role.isBlank() || side == null) return null;
        return new BlockAction() {
            private int remaining(IItemHandler inventory) {
                long stored = 0;
                for (int i = 0; i < inventory.getSlots(); i++) stored += inventory.getStackInSlot(i).getCount();
                return remainingCapacity(stored, inventoryLimit);
            }
            private IItemHandler inventory(BlockActionContext ctx) {
                if (!ctx.level().isLoaded(ctx.pos())) return null;
                return BlockInventories.itemHandler(ctx.level(), ctx.pos(), side);
            }

            @Override public boolean canRun(BlockActionContext ctx) {
                IItemHandler inventory = inventory(ctx);
                if (inventory == null || slot >= inventory.getSlots()) return false;
                ItemStack stored = inventory.getStackInSlot(slot);
                if (stored.getCount() >= limit || remaining(inventory) <= 0) return false;
                ItemStack supplied = ctx.itemRole(role);
                // Job discovery checks capacity before fetching an item. Material selection
                // later probes the same action with a concrete stack and native insertion rules.
                if (supplied.isEmpty()) return true;
                if (rejectRemainders && !supplied.getCraftingRemainingItem().isEmpty()) return false;
                ItemStack offer = supplied.copy();
                offer.setCount(Math.min(remaining(inventory), offerCount(supplied.getCount(), stored.getCount(), count, limit)));
                return !offer.isEmpty() && inventory.insertItem(slot, offer, true).getCount() < offer.getCount();
            }

            @Override public void run(BlockActionContext ctx) {
                if (ctx.itemRole(role).isEmpty() || !canRun(ctx)) { ctx.fail(); return; }
                IItemHandler inventory = inventory(ctx);
                if (inventory == null || slot >= inventory.getSlots()) { ctx.fail(); return; }
                ItemStack supplied = ctx.itemRole(role);
                int amount = Math.min(remaining(inventory), offerCount(supplied.getCount(), inventory.getStackInSlot(slot).getCount(), count, limit));
                if (amount == 0) { ctx.fail(); return; }
                ItemStack offer = supplied.copy();
                offer.setCount(amount);
                ItemStack rejected = inventory.insertItem(slot, offer, false);
                int inserted = amount - rejected.getCount();
                if (inserted <= 0) { ctx.fail(); return; }
                supplied.shrink(inserted);
                ctx.setItemRole(role, supplied);
            }
        };
    }

    static int offerCount(int available, int stored, int count, int limit) {
        return Math.max(0, Math.min(available, Math.min(count, limit - stored)));
    }
    static int remainingCapacity(long stored, int limit) {
        return (int) Math.max(0, limit - Math.min(Integer.MAX_VALUE, stored));
    }
}
