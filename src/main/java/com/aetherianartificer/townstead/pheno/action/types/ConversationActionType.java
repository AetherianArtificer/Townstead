package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.dialogue.conversation.ConversationEngine;
import com.aetherianartificer.townstead.pheno.action.*;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/** Optional social invitation using the existing self/counterpart targeting contract. */
public final class ConversationActionType implements ActionType {
    public static final String KEY = "pheno:conversation";
    @Override public String key() { return KEY; }
    @Override public Action parse(JsonObject json) {
        ResourceLocation topic = json.has("topic") ? ResourceLocation.tryParse(GsonHelper.getAsString(json, "topic")) : null;
        if (json.has("topic") && topic == null) return null;
        return context -> {
            if (context.entity() instanceof VillagerEntityMCA actor && context.other() instanceof VillagerEntityMCA other)
                ConversationEngine.request(actor, other, topic, false);
        };
    }
}
