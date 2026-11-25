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

/**
 * Takes items out of one slot of a block's public inventory, through the side's own extraction
 * rules, and hands them back as the action's result. The counterpart of {@code pheno:insert_item}:
 * a machine's byproduct leaves the way a hopper below would take it.
 */
public final class ExtractItemBlockActionType implements BlockActionType {
    @Override public String key() { return "pheno:extract_item"; }

    @Override public BlockAction parse(JsonObject json) {
        int slot = GsonHelper.getAsInt(json, "slot", -1);
        int count = GsonHelper.getAsInt(json, "count", 64);
        Direction side = Direction.byName(GsonHelper.getAsString(json, "side", "down"));
        if (slot < 0 || count < 1 || side == null) return null;
        return new BlockAction() {
            private IItemHandler inventory(BlockActionContext ctx) {
                if (!ctx.level().isLoaded(ctx.pos())) return null;
                return BlockInventories.itemHandler(ctx.level(), ctx.pos(), side);
            }

            @Override public boolean canRun(BlockActionContext ctx) {
                IItemHandler inventory = inventory(ctx);
                return inventory != null && slot < inventory.getSlots()
                        && !inventory.extractItem(slot, count, true).isEmpty();
            }

            @Override public void run(BlockActionContext ctx) {
                IItemHandler inventory = inventory(ctx);
                if (inventory == null || slot >= inventory.getSlots()) { ctx.fail(); return; }
                ItemStack taken = inventory.extractItem(slot, count, false);
                if (taken.isEmpty()) { ctx.fail(); return; }
                ctx.returnItem(taken);
            }
        };
    }
}
