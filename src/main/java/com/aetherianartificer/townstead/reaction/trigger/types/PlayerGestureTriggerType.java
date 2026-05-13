package com.aetherianartificer.townstead.reaction.trigger.types;

import com.aetherianartificer.townstead.reaction.TriggerIndex;
import com.aetherianartificer.townstead.reaction.trigger.TriggerInstance;
import com.aetherianartificer.townstead.reaction.trigger.TriggerType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/**
 * Fires when a player runs an emote with the named id near (and looking
 * at) a villager. The emote reference is keyed lowercased — same form as
 * binding refs — so authors can match the casing they see in their emote
 * folder.
 */
public final class PlayerGestureTriggerType implements TriggerType {
    public static final String KEY = "player_gesture";

    @Override
    public String key() {
        return KEY;
    }

    public record Instance(String emoteName, float maxDistance, float minDot) implements TriggerInstance {
        @Override
        public String typeKey() {
            return KEY;
        }
    }

    @Override
    public TriggerInstance parse(JsonObject json) {
        String raw = GsonHelper.getAsString(json, "emote", "");
        if (raw.isBlank()) return null;
        // Accept either "emotecraft:Name" or bare "Name"; index by the trailing name only.
        int colon = raw.indexOf(':');
        String name = colon >= 0 ? raw.substring(colon + 1) : raw;
        if (name.isBlank()) return null;
        float maxDistance = GsonHelper.getAsFloat(json, "max_distance", 6.0F);
        float minDot = GsonHelper.getAsFloat(json, "min_dot", 0.6F);
        return new Instance(name.toLowerCase(Locale.ROOT), maxDistance, minDot);
    }

    @Override
    public void index(TriggerInstance instance, ResourceLocation reactionId, TriggerIndex.Builder builder) {
        if (instance instanceof Instance gesture) {
            builder.add(KEY, gesture.emoteName(), reactionId);
        }
    }
}
