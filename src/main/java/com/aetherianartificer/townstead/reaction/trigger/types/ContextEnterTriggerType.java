package com.aetherianartificer.townstead.reaction.trigger.types;

import com.aetherianartificer.townstead.reaction.ReactionConditions;
import com.aetherianartificer.townstead.reaction.TriggerIndex;
import com.aetherianartificer.townstead.reaction.trigger.TriggerInstance;
import com.aetherianartificer.townstead.reaction.trigger.TriggerType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * Fires once when a villager's resolved context tag set newly includes
 * all listed tags. Indexed under each required tag separately; the event
 * site re-checks match-all against the full incoming tag set before
 * dispatching, so partial-tag matches don't false-fire.
 */
public final class ContextEnterTriggerType implements TriggerType {
    public static final String KEY = "context_enter";

    @Override
    public String key() {
        return KEY;
    }

    public record Instance(List<String> requiredTags) implements TriggerInstance {
        @Override
        public String typeKey() {
            return KEY;
        }
    }

    @Override
    public TriggerInstance parse(JsonObject json) {
        List<String> tags = ReactionConditions.parseStringArray(json, "tags");
        if (tags.isEmpty()) return null;
        return new Instance(tags.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList());
    }

    @Override
    public void index(TriggerInstance instance, ResourceLocation reactionId, TriggerIndex.Builder builder) {
        if (instance instanceof Instance ctx) {
            for (String tag : ctx.requiredTags()) {
                builder.add(KEY, tag, reactionId);
            }
        }
    }
}
