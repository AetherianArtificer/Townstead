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

/**
 * Puts an {@code item} on cooldown for the actor (Apugli's {@code item_cooldown}).
 * Player-only, since item cooldowns live on the player's {@code ItemCooldowns}.
 *
 * <p>JSON: {@code { "type":"pheno:item_cooldown", "item":"minecraft:ender_pearl",
 * "cooldown":100 }}</p>
 */
public final class ItemCooldownActionType implements ActionType {

    public static final String KEY = "pheno:item_cooldown";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "item", ""));
        if (id == null) return null;
        int cooldown = GsonHelper.getAsInt(json, "cooldown", 20);
        return ctx -> {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != null && ctx.entity() instanceof Player player) {
                player.getCooldowns().addCooldown(item, cooldown);
            }
        };
    }
}
