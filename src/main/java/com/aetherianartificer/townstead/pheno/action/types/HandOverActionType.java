package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Hands the bearer's main-hand stack to the focus. A player receives it in the pack, with any
 * overflow dropped at their feet. Another creature takes it in an empty main hand, or it drops at
 * their feet. Fails when the bearer holds nothing or the focus is the bearer.
 *
 * <p>JSON: {@code { "type":"pheno:hand_over" }}</p>
 */
public final class HandOverActionType implements ActionType {

    public static final String KEY = "pheno:hand_over";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> {
            LivingEntity giver = ctx.origin();
            LivingEntity taker = ctx.entity();
            if (giver == null || taker == null || giver == taker || giver.getMainHandItem().isEmpty()) {
                ctx.fail();
                return;
            }
            ItemStack stack = giver.getMainHandItem().copy();
            giver.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (taker instanceof Player player) {
                if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
            } else if (taker.getMainHandItem().isEmpty()) {
                taker.setItemInHand(InteractionHand.MAIN_HAND, stack);
            } else {
                taker.level().addFreshEntity(new ItemEntity(taker.level(), taker.getX(), taker.getY(), taker.getZ(), stack));
            }
        };
    }
}
