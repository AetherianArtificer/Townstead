package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.particle.DataParticleDef;
import com.aetherianartificer.townstead.pheno.screen.ScreenEffect;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/**
 * Plays a screen cue for the focus, when it is a player: {@code flash}, {@code vignette},
 * {@code tint}, {@code shake} or {@code fov}, in {@code color} (default white), at
 * {@code intensity} (0-1; for {@code fov} the fraction the view widens, negative narrows), over
 * {@code duration} ticks. {@code radius} also plays it for every other player that close, so a
 * stomp shakes everyone nearby. Players' own screen-effect and reduced-motion settings scale or
 * suppress every cue.
 *
 * <p>JSON: {@code { "type":"pheno:screen_effect", "effect":"vignette", "color":"#8FD3FF",
 * "intensity":0.5, "duration":30 }}</p>
 */
public final class ScreenEffectActionType implements ActionType {

    public static final String KEY = "pheno:screen_effect";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ScreenEffect.Kind kind = ScreenEffect.Kind.byKey(GsonHelper.getAsString(json, "effect", ""));
        if (kind == null) return null;
        Integer color = DataParticleDef.rgb(GsonHelper.getAsString(json, "color", "#FFFFFF"));
        if (color == null) return null;
        float intensity = GsonHelper.getAsFloat(json, "intensity", kind == ScreenEffect.Kind.FOV ? 0.05f : 0.5f);
        intensity = kind == ScreenEffect.Kind.FOV ? Math.max(-0.3f, Math.min(0.3f, intensity))
                : Math.max(0f, Math.min(1f, intensity));
        int duration = Math.max(1, Math.min(200, GsonHelper.getAsInt(json, "duration", 20)));
        double radius = Math.max(0, GsonHelper.getAsDouble(json, "radius", 0));
        float strength = intensity;
        int rgb = color;
        return ctx -> {
            if (!(ctx.entity().level() instanceof ServerLevel level)) return;
            if (ctx.entity() instanceof ServerPlayer player) ScreenEffect.send(player, kind, rgb, strength, duration);
            if (radius <= 0) return;
            for (ServerPlayer other : level.players()) {
                if (other != ctx.entity() && other.distanceToSqr(ctx.entity()) <= radius * radius) {
                    ScreenEffect.send(other, kind, rgb, strength, duration);
                }
            }
        };
    }
}
