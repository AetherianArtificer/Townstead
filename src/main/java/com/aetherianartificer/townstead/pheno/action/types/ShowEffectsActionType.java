package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shows the focus's name and its status effects, each with its level and the time it has left, to
 * the bearer above the hotbar. Nothing happens when the bearer is not a player.
 *
 * <p>JSON: {@code { "type":"pheno:show_effects" }}</p>
 */
public final class ShowEffectsActionType implements ActionType {

    public static final String KEY = "pheno:show_effects";
    private static final int MAX_SHOWN = 4;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> {
            LivingEntity focus = ctx.entity();
            if (focus == null || !(ctx.origin() instanceof ServerPlayer viewer)) return;
            MutableComponent line = Component.empty().append(focus.getDisplayName());
            int shown = 0;
            for (MobEffectInstance effect : focus.getActiveEffects()) {
                if (shown++ >= MAX_SHOWN) {
                    line.append(Component.literal("  +" + (focus.getActiveEffects().size() - MAX_SHOWN))
                            .withStyle(ChatFormatting.GRAY));
                    break;
                }
                line.append("   ").append(Component.translatable(effect.getDescriptionId()));
                if (effect.getAmplifier() > 0) {
                    line.append(" ").append(Component.translatable("enchantment.level." + (effect.getAmplifier() + 1)));
                }
                if (!effect.isInfiniteDuration()) {
                    int seconds = effect.getDuration() / 20;
                    line.append(Component.literal(" " + seconds / 60 + ":" + String.format(java.util.Locale.ROOT,
                            "%02d", seconds % 60)).withStyle(ChatFormatting.YELLOW));
                }
            }
            viewer.displayClientMessage(line, true);
        };
    }
}
