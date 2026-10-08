package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.performance.PerformanceProviders;
import com.aetherianartificer.townstead.performance.PerformanceRequest;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/**
 * Requests a semantic performance without naming an animation implementation. A provider may
 * render the cue through Emotecraft, a bbmodel rig, or animation JSON; the built-in fallback is
 * always available when the authored cue permits it.
 */
public final class PerformanceActionType implements ActionType {
    public static final String KEY = "pheno:performance";

    @Override public String key() { return KEY; }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "performance", ""));
        if (id == null) return null;
        String channel = GsonHelper.getAsString(json, "channel", "social").trim();
        if (channel.isEmpty()) return null;
        int duration = Math.max(1, GsonHelper.getAsInt(json, "duration_ticks", 40));
        int priority = GsonHelper.getAsInt(json, "priority", 0);
        PerformanceRequest.Fallback fallback;
        try {
            fallback = PerformanceRequest.Fallback.valueOf(
                    GsonHelper.getAsString(json, "fallback", "vanilla_gesture").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return ctx -> {
            if (ctx.level() instanceof ServerLevel level) {
                PerformanceProviders.play(level, new PerformanceRequest(ctx.entity(), id, channel,
                        duration, priority, fallback));
            }
        };
    }
}
