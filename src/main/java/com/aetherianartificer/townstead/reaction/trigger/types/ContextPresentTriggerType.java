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
 * Sibling to {@code context_enter}: fires every {@code ContextTickHook}
 * stride while all required tags are present, rather than once on the
 * transition. The reaction's {@code cooldown_ticks} is the actual rate
 * limit — use it to control how often the reaction repeats while the
 * tag holds.
 *
 * <p>Example use: dance while {@code near_music} stays true.</p>
 */
public final class ContextPresentTriggerType implements TriggerType {
    public static final String KEY = "context_present";

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
