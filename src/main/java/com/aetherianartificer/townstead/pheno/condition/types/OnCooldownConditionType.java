package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * True when an {@code item} is on cooldown for the player (Apugli's {@code on_cooldown}).
 * Player-only, since item cooldowns live on the player's {@code ItemCooldowns}; never
 * matches on villagers.
 *
 * <p>JSON: {@code { "type":"pheno:on_cooldown", "item":"minecraft:ender_pearl" }}</p>
 */
public final class OnCooldownConditionType implements ConditionType {

    public static final String KEY = "pheno:on_cooldown";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "item", ""));
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.get(id);
        return ctx -> ctx.entity() instanceof Player player && player.getCooldowns().isOnCooldown(item);
    }
}
