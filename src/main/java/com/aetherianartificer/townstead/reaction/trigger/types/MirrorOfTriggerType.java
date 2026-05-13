package com.aetherianartificer.townstead.reaction.trigger.types;

import com.aetherianartificer.townstead.reaction.TriggerIndex;
import com.aetherianartificer.townstead.reaction.trigger.TriggerInstance;
import com.aetherianartificer.townstead.reaction.trigger.TriggerType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Fires on a nearby villager when a source villager plays the named
 * reaction. {@code require_tags_match} restricts the mirror to source
 * reactions sharing at least one tag with this one; the event site
 * applies that filter.
 */
public final class MirrorOfTriggerType implements TriggerType {
    public static final String KEY = "mirror_of";

    @Override
    public String key() {
        return KEY;
    }

    public record Instance(String sourceReactionId, boolean requireTagsMatch) implements TriggerInstance {
        @Override
        public String typeKey() {
            return KEY;
        }
    }

    @Override
    public TriggerInstance parse(JsonObject json) {
        String source = GsonHelper.getAsString(json, "reaction", "");
        if (source.isBlank()) return null;
        boolean requireTags = GsonHelper.getAsBoolean(json, "require_tags_match", false);
        return new Instance(source, requireTags);
    }

    @Override
    public void index(TriggerInstance instance, ResourceLocation reactionId, TriggerIndex.Builder builder) {
        if (instance instanceof Instance mirror) {
            builder.add(KEY, mirror.sourceReactionId(), reactionId);
        }
    }
}
