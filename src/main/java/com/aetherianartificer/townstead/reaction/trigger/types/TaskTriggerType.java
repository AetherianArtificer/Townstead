package com.aetherianartificer.townstead.reaction.trigger.types;

import com.aetherianartificer.townstead.reaction.TriggerIndex;
import com.aetherianartificer.townstead.reaction.trigger.TriggerInstance;
import com.aetherianartificer.townstead.reaction.trigger.TriggerType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/**
 * Fires when a villager task transitions through a named phase. The
 * index key is the {@code taskId@phase} composite so event sites can
 * look up matching reactions by the exact transition.
 */
public final class TaskTriggerType implements TriggerType {
    public static final String KEY = "task";

    @Override
    public String key() {
        return KEY;
    }

    public record Instance(String taskId, String phase) implements TriggerInstance {
        @Override
        public String typeKey() {
            return KEY;
        }

        public String indexKey() {
            return composite(taskId, phase);
        }
    }

    public static String composite(String taskId, String phase) {
        return taskId.toLowerCase(Locale.ROOT) + '@' + phase.toLowerCase(Locale.ROOT);
    }

    @Override
    public TriggerInstance parse(JsonObject json) {
        String taskId = GsonHelper.getAsString(json, "task", "");
        String phase = GsonHelper.getAsString(json, "phase", "");
        if (taskId.isBlank() || phase.isBlank()) return null;
        return new Instance(taskId, phase);
    }

    @Override
    public void index(TriggerInstance instance, ResourceLocation reactionId, TriggerIndex.Builder builder) {
        if (instance instanceof Instance task) {
            builder.add(KEY, task.indexKey(), reactionId);
        }
    }
}
