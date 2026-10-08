package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.item.StoryItems;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Gives the player of the action a story item ({@code data/<ns>/story_item/}), made in their
 * language. With {@code drop}, it is dropped at the feet of the action's focus instead (a story
 * villager dropping something, or something found on the ground).
 * <pre>
 * { "type": "pheno:give_story_item", "item": "townstead:court_signet", "drop": true }
 * </pre>
 */
public final class GiveStoryItemActionType implements ActionType {
    public static final String KEY = "pheno:give_story_item";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "item", ""));
        if (id == null) return null;
        int count = Math.max(1, GsonHelper.getAsInt(json, "count", 1));
        boolean drop = GsonHelper.getAsBoolean(json, "drop", false);
        return ctx -> {
            ServerPlayer player = ctx.entity() instanceof ServerPlayer p ? p : ctx.other() instanceof ServerPlayer p ? p : null;
            ItemStack stack = player == null ? ItemStack.EMPTY : StoryItems.make(id, count, player);
            if (stack.isEmpty()) {
                ctx.fail();
                return;
            }
            if (drop && ctx.entity() != null) {
                var at = ctx.entity();
                at.level().addFreshEntity(new ItemEntity(at.level(), at.getX(), at.getY() + 0.5, at.getZ(), stack));
                return;
            }
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        };
    }
}
