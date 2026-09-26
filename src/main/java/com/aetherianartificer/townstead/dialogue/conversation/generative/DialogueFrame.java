package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** The order of pools that builds a line for one move. */
public record DialogueFrame(ResourceLocation move, Set<String> subjects, @Nullable ResourceLocation voice, double weight,
                            @Nullable String key, List<Slot> slots) {
    public enum StanceMode { NONE, MATCH, OPPOSE }

    public enum Kind { POOL, ANSWER, RESPONSE }

    /**
     * A pool position, the position where the answer to an open question goes, or the position where
     * the reply to an open pair goes. Answer and response slots draw from every voice's parts.
     */
    public record Slot(@Nullable ResourceLocation pool, StanceMode stance, Kind kind) {
        public static final Slot ANSWER = new Slot(null, StanceMode.NONE, Kind.ANSWER);
        public static final Slot RESPONSE = new Slot(null, StanceMode.NONE, Kind.RESPONSE);
        public Slot(ResourceLocation pool, StanceMode stance) { this(pool, stance, Kind.POOL); }
        public boolean answer() { return kind == Kind.ANSWER; }
        public boolean response() { return kind == Kind.RESPONSE; }
        public boolean pooled() { return kind == Kind.POOL; }
    }

    public boolean hasAnswerSlot() {
        return slots.stream().anyMatch(Slot::answer);
    }

    public boolean hasResponseSlot() {
        return slots.stream().anyMatch(Slot::response);
    }

    static List<DialogueFrame> parseFile(JsonObject json) {
        Json.only(json, "schema", "frames", "mods");
        List<DialogueFrame> out = new ArrayList<>();
        var frames = GsonHelper.getAsJsonArray(json, "frames");
        for (int i = 0; i < frames.size(); i++) {
            try {
                out.add(parse(frames.get(i).getAsJsonObject()));
            } catch (RuntimeException ex) {
                throw Json.bad("frames[" + i + "]", ex.getMessage());
            }
        }
        return out;
    }

    /** {@code "pool"}, {@code "pool match"}, {@code "pool oppose"}, {@code "@answer"} or {@code "@response"}. */
    private static Slot shorthand(String raw, String field) {
        String[] words = raw.trim().split("\\s+");
        if (words.length == 1 && words[0].equals("@answer")) return Slot.ANSWER;
        if (words.length == 1 && words[0].equals("@response")) return Slot.RESPONSE;
        if (words.length > 2 || words[0].startsWith("@")) throw Json.bad(field, "unknown slot " + raw);
        StanceMode mode = words.length == 1 ? StanceMode.NONE : switch (words[1]) {
            case "match" -> StanceMode.MATCH;
            case "oppose" -> StanceMode.OPPOSE;
            default -> throw Json.bad(field, "stance must be match or oppose");
        };
        return new Slot(Json.parseId(words[0]), mode);
    }

    static DialogueFrame parse(JsonObject json) {
        Json.only(json, "move", "subjects", "voice", "weight", "key", "slots");
        List<Slot> slots = new ArrayList<>();
        var raw = GsonHelper.getAsJsonArray(json, "slots");
        if (raw.isEmpty() || raw.size() > 6) throw Json.bad("slots", "must contain 1..6 slots");
        for (int i = 0; i < raw.size(); i++) {
            if (raw.get(i).isJsonPrimitive()) {
                slots.add(shorthand(raw.get(i).getAsString(), "slots[" + i + "]"));
                continue;
            }
            JsonObject slot = raw.get(i).getAsJsonObject();
            if (slot.has("answer")) {
                Json.only(slot, "answer");
                slots.add(Slot.ANSWER);
                continue;
            }
            if (slot.has("response")) {
                Json.only(slot, "response");
                slots.add(Slot.RESPONSE);
                continue;
            }
            Json.only(slot, "pool", "stance");
            StanceMode mode = switch (GsonHelper.getAsString(slot, "stance", "none")) {
                case "match" -> StanceMode.MATCH;
                case "oppose" -> StanceMode.OPPOSE;
                case "none" -> StanceMode.NONE;
                default -> throw Json.bad("slots[" + i + "].stance", "must be match or oppose");
            };
            slots.add(new Slot(Json.requiredId(slot, "pool"), mode));
        }
        return new DialogueFrame(Json.requiredId(json, "move"), Json.subjectSet(json, "subjects"), Json.id(json, "voice"),
                Json.number(json, "weight", 1, 0.0001, 100), json.has("key") ? GsonHelper.getAsString(json, "key") : null,
                List.copyOf(slots));
    }
}
