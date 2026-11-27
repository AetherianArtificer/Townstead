package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shows the focus's name and health to the bearer, above the hotbar. Nothing happens when the
 * bearer is not a player.
 *
 * <p>JSON: {@code { "type":"pheno:show_health" }}</p>
 */
public final class ShowHealthActionType implements ActionType {

    public static final String KEY = "pheno:show_health";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> {
            LivingEntity focus = ctx.entity();
            if (focus == null || !(ctx.origin() instanceof ServerPlayer viewer)) return;
            float health = focus.getHealth();
            float max = focus.getMaxHealth();
            ChatFormatting color = health <= max * 0.3f ? ChatFormatting.RED
                    : health <= max * 0.6f ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
            Component line = Component.empty().append(focus.getDisplayName()).append("  ")
                    .append(Component.literal(format(health) + " / " + format(max)).withStyle(color));
            viewer.displayClientMessage(line, true);
        };
    }

    private static String format(float value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
