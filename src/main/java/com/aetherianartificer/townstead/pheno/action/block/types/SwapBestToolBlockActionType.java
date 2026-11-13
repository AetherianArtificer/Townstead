package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Puts the best tool for the target block in the cause's hand: the stack in the pack that breaks
 * the block fastest swaps places with the held stack. Only a player carries a pack. Fails when the
 * held stack is already the fastest.
 *
 * <p>JSON: {@code { "type":"pheno:swap_best_tool" }}</p>
 */
public final class SwapBestToolBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:swap_best_tool";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        return ctx -> {
            if (!(ctx.cause() instanceof Player player)) {
                ctx.fail();
                return;
            }
            BlockState state = ctx.level().getBlockState(ctx.pos());
            Inventory inventory = player.getInventory();
            int held = inventory.selected;
            int best = held;
            float fastest = inventory.items.get(held).getDestroySpeed(state);
            for (int slot = 0; slot < inventory.items.size(); slot++) {
                float speed = inventory.items.get(slot).getDestroySpeed(state);
                if (speed > fastest) {
                    fastest = speed;
                    best = slot;
                }
            }
            if (best == held) {
                ctx.fail();
                return;
            }
            ItemStack inHand = inventory.items.get(held);
            inventory.items.set(held, inventory.items.get(best));
            inventory.items.set(best, inHand);
        };
    }
}
