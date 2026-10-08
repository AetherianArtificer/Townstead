package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Takes {@code count} matching items from the player of the action (a quest hand-in). Matches an
 * {@code item} id, an item {@code tag}, or a {@code story_item}. Takes nothing and fails when they carry fewer.
 * <pre>
 * { "type": "pheno:take_items", "tag": "townstead:garlic", "count": 16 }
 * </pre>
 */
public final class TakeItemsActionType implements ActionType {
    public static final String KEY = "pheno:take_items";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        int count = Math.max(1, GsonHelper.getAsInt(json, "count", 1));
        Predicate<ItemStack> matches;
        if (json.has("story_item")) {
            ResourceLocation storyId = DataPackLang.parseId(GsonHelper.getAsString(json, "story_item"));
            if (storyId == null) return null;
            matches = stack -> com.aetherianartificer.townstead.item.StoryItems.is(stack, storyId);
        } else if (json.has("tag")) {
            ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "tag"));
            if (id == null) return null;
            TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
            matches = stack -> stack.is(tag);
        } else {
            ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "item", ""));
            if (id == null) return null;
            matches = stack -> id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        return ctx -> {
            Player player = ctx.other() instanceof Player p ? p : ctx.entity() instanceof Player p ? p : null;
            if (player == null || count(player, matches) < count) {
                ctx.fail();
                return;
            }
            int left = count;
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty() || !matches.test(stack)) continue;
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
            inventory.setChanged();
        };
    }

    private static int count(Player player, Predicate<ItemStack> matches) {
        int total = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && matches.test(stack)) total += stack.getCount();
        }
        return total;
    }
}
