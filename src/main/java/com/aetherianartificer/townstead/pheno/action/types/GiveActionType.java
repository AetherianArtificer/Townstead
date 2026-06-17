package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Gives the actor an item stack (player-only); overflow drops at their feet (Apoli's
 * {@code give}).
 *
 * <p>JSON: {@code { "type":"pheno:give", "item":"minecraft:bread", "count":3 }}</p>
 */
public final class GiveActionType implements ActionType {

    public static final String KEY = "pheno:give";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "item", ""));
        if (id == null) return null;
        int count = Math.max(1, GsonHelper.getAsInt(json, "count", 1));
        return ctx -> {
            if (!(ctx.entity() instanceof Player player)) return;
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == null) return;
            ItemStack stack = new ItemStack(item, count);
            if (!player.addItem(stack)) player.drop(stack, false);
        };
    }
}
