package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Drops an item stack at the actor's position (Apugli's {@code spawn_item}). The stack
 * is {@code amount} of {@code item}.
 *
 * <p>JSON: {@code { "type":"pheno:spawn_item", "item":"minecraft:emerald",
 * "amount":1 }}</p>
 */
public final class SpawnItemActionType implements ActionType {

    public static final String KEY = "pheno:spawn_item";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "item", ""));
        if (id == null) return null;
        int amount = GsonHelper.getAsInt(json, "amount", 1);
        return ctx -> {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == null || ctx.entity().level().isClientSide) return;
            ctx.entity().spawnAtLocation(new ItemStack(item, Math.max(1, amount)));
        };
    }
}
