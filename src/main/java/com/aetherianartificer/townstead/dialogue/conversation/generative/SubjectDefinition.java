package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.aetherianartificer.townstead.dialogue.conversation.ConversationTopic;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.*;

/** A thing to talk about. Its source produces live instances from game state. */
public record SubjectDefinition(ResourceLocation id, ResourceLocation sourceType, JsonObject source, String register,
                                String valence, List<String> symmetric, Map<String, SlotSpec> slots, List<String> requires,
                                Map<String, Double> interests, Set<String> contentTags, double weight,
                                ConversationTopic.Gate gate) {
    public static final String SCHEMA = "townstead:conversation_subject/v1";
    public static final Set<String> SLOT_TYPES = Set.of("person", "place", "organization", "profession", "item", "title",
            "topic_phrase", "time_ago", "amount", "text");

    public record SlotSpec(String type, List<String> from, boolean required) {}

    static SubjectDefinition parse(ResourceLocation id, JsonObject json) {
        Json.only(json, "schema", "source", "register", "valence", "symmetric", "slots", "requires", "interests",
                "content_tags", "weight", "context", "relationship", "personality_weights", "relationship_weights", "when",
                "evaluation", "mods");
        JsonObject source = GsonHelper.getAsJsonObject(json, "source");
        ResourceLocation type = Json.requiredId(source, "type");
        Map<String, SlotSpec> slots = new LinkedHashMap<>();
        if (json.has("slots")) for (var e : GsonHelper.getAsJsonObject(json, "slots").entrySet()) {
            JsonObject slot = e.getValue().getAsJsonObject();
            Json.only(slot, "type", "from", "required");
            String slotType = GsonHelper.getAsString(slot, "type");
            if (!SLOT_TYPES.contains(slotType)) throw Json.bad("slots." + e.getKey() + ".type", "unknown slot type " + slotType);
            if ("self".equals(e.getKey())) throw Json.bad("slots.self", "self is reserved");
            slots.put(e.getKey(), new SlotSpec(slotType, Json.strings(slot, "from"), GsonHelper.getAsBoolean(slot, "required", true)));
        }
        String valence = GsonHelper.getAsString(json, "valence", "neutral");
        if (!Set.of("positive", "negative", "neutral", "from_source").contains(valence)) throw Json.bad("valence", "unknown " + valence);
        return new SubjectDefinition(id, type, source, GsonHelper.getAsString(json, "register", "ambient"), valence,
                Json.strings(json, "symmetric"), Map.copyOf(slots), Json.strings(json, "requires"),
                Json.numbers(json, "interests", 0, 1), Json.stringSet(json, "content_tags"),
                Json.number(json, "weight", 1, 0.0001, 100), ConversationTopic.gate(json));
    }
}
