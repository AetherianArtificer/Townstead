package com.aetherianartificer.townstead.habitus.action.item.types;

import com.aetherianartificer.townstead.habitus.action.item.ItemAction;
import com.aetherianartificer.townstead.habitus.action.item.ItemActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;

/**
 * Puts the stack's item on cooldown for the holder (Apugli's item {@code cooldown}).
 * No-op unless the holder is a player, since item cooldowns live on the player.
 *
 * <p>JSON: {@code { "type":"townstead_origins:cooldown", "cooldown":40 }}</p>
 */
public final class CooldownItemActionType implements ItemActionType {

    public static final String KEY = "townstead_origins:cooldown";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public ItemAction parse(JsonObject json) {
        int cooldown = GsonHelper.getAsInt(json, "cooldown", 20);
        return ctx -> {
            if (ctx.holder() instanceof Player player && !ctx.stack().isEmpty()) {
                player.getCooldowns().addCooldown(ctx.stack().getItem(), cooldown);
            }
        };
    }
}
