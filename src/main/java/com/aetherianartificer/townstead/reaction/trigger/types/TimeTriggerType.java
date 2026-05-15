package com.aetherianartificer.townstead.reaction.trigger.types;

import com.aetherianartificer.townstead.reaction.TriggerIndex;
import com.aetherianartificer.townstead.reaction.trigger.TriggerInstance;
import com.aetherianartificer.townstead.reaction.trigger.TriggerType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/**
 * Stride-evaluated trigger: every {@code interval_ticks}, when the world
 * time phase matches, the location tick hook rolls candidates. Indexed
 * by phase so the hook can iterate matching reactions cheaply.
 */
public final class TimeTriggerType implements TriggerType {
    public static final String KEY = "time";

    @Override
    public String key() {
        return KEY;
    }

    public record Instance(String phase, int intervalTicks) implements TriggerInstance {
        @Override
        public String typeKey() {
            return KEY;
        }
    }

    @Override
    public TriggerInstance parse(JsonObject json) {
        String phase = GsonHelper.getAsString(json, "phase", "");
        if (phase.isBlank()) return null;
        int interval = Math.max(20, GsonHelper.getAsInt(json, "interval_ticks", 1200));
        return new Instance(phase.toLowerCase(Locale.ROOT), interval);
    }

    @Override
    public void index(TriggerInstance instance, ResourceLocation reactionId, TriggerIndex.Builder builder) {
        if (instance instanceof Instance time) {
            builder.add(KEY, time.phase(), reactionId);
        }
    }
}
