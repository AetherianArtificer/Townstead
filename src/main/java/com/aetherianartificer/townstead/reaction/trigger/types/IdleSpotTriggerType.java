package com.aetherianartificer.townstead.reaction.trigger.types;

import com.aetherianartificer.townstead.reaction.TriggerIndex;
import com.aetherianartificer.townstead.reaction.trigger.TriggerInstance;
import com.aetherianartificer.townstead.reaction.trigger.TriggerType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Fires when a villager idles near a player-placed POI of the named
 * spot type. The event site (POI tick hook) supplies the spot id.
 */
public final class IdleSpotTriggerType implements TriggerType {
    public static final String KEY = "idle_spot";

    @Override
    public String key() {
        return KEY;
    }

    public record Instance(String spotId) implements TriggerInstance {
        @Override
        public String typeKey() {
            return KEY;
        }
    }

    @Override
    public TriggerInstance parse(JsonObject json) {
        String spot = GsonHelper.getAsString(json, "spot", "");
        if (spot.isBlank()) return null;
        return new Instance(spot);
    }

    @Override
    public void index(TriggerInstance instance, ResourceLocation reactionId, TriggerIndex.Builder builder) {
        if (instance instanceof Instance idle) {
            builder.add(KEY, idle.spotId(), reactionId);
        }
    }
}
