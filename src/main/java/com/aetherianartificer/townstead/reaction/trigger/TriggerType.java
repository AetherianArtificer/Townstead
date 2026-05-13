package com.aetherianartificer.townstead.reaction.trigger;

import com.aetherianartificer.townstead.reaction.TriggerIndex;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

/**
 * Pluggable trigger-type contract. Implementations parse one trigger JSON
 * object into a {@link TriggerInstance}, then index its lookup keys so
 * the dispatcher can find owning reactions in O(matches) at fire time.
 */
public interface TriggerType {
    String key();

    TriggerInstance parse(JsonObject json);

    void index(TriggerInstance instance, ResourceLocation reactionId, TriggerIndex.Builder builder);
}
