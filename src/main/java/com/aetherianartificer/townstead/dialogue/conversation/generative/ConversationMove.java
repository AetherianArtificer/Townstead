package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.aetherianartificer.townstead.dialogue.conversation.ConversationTopic;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** A dialogue act. It carries no text; frames and parts do. */
public record ConversationMove(ResourceLocation id, Role role, SubjectUse subject, boolean finalLine, boolean spreads,
                               @Nullable ResourceLocation followUp, boolean initiatorOnly,
                               ConversationTopic.Cue gesture, ConversationTopic.Cue listener) {
    public enum Role { OPENING, TOPIC, EXPANSION, CLOSING, PAUSE }
    public enum SubjectUse { REQUIRED, OPTIONAL, NONE }

    public static final String SCHEMA = "townstead:conversation_move/v1";

    static ConversationMove parse(ResourceLocation id, JsonObject json) {
        Json.only(json, "schema", "role", "subject", "final", "spreads", "follow_up", "initiator_only", "gesture", "listener", "mods");
        Role role = enumValue(Role.class, GsonHelper.getAsString(json, "role", "topic"), "role");
        SubjectUse subject = enumValue(SubjectUse.class, GsonHelper.getAsString(json, "subject", "optional"), "subject");
        return new ConversationMove(id, role, subject, GsonHelper.getAsBoolean(json, "final", role == Role.CLOSING),
                GsonHelper.getAsBoolean(json, "spreads", false), Json.id(json, "follow_up"),
                GsonHelper.getAsBoolean(json, "initiator_only", false),
                ConversationTopic.cue(json, "gesture"), ConversationTopic.cue(json, "listener"));
    }

    static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String field) {
        try { return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw Json.bad(field, "unknown value " + raw); }
    }
}
