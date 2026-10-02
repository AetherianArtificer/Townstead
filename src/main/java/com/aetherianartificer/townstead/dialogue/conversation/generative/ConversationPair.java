package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * A kind of line that expects a kind of reply: a complaint expects sympathy, a joke a laugh or a groan.
 * A part that {@code opens} a pair obliges the other speaker's next line to start with a part that
 * {@code responds} to it. A required pair is always answered; an optional one only by {@code chance}.
 */
public record ConversationPair(ResourceLocation id, boolean required, double chance) {
    public static final String SCHEMA = "townstead:conversation_pair/v1";

    static ConversationPair parse(ResourceLocation id, JsonObject json) {
        Json.only(json, "schema", "uptake", "chance", "mods");
        String uptake = GsonHelper.getAsString(json, "uptake", "required");
        boolean required = switch (uptake) {
            case "required" -> true;
            case "optional" -> false;
            default -> throw Json.bad("uptake", "must be required or optional");
        };
        return new ConversationPair(id, required, required ? 1 : Json.number(json, "chance", 0.5, 0, 1));
    }
}
