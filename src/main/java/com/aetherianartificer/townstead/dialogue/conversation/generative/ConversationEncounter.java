package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** The shape of a whole meeting: how it opens, how long it runs, how it pauses and how it ends. */
public record ConversationEncounter(ResourceLocation id, Context context, List<Sequence> opening, List<Sequence> openingAgain,
                                    int topicsMin, int topicsMax, boolean untilDeparture, Chattiness chattiness,
                                    int expansionsMax, Pause pause, @Nullable ResourceLocation bridgePool,
                                    List<Sequence> closing) {
    public enum Context { IDLE, HANGOUT }
    public static final String SCHEMA = "townstead:conversation_encounter/v1";

    /** A sequence of moves. A closing can also depend on how interested the less interested speaker still is. */
    public record Sequence(List<ResourceLocation> moves, double weight, int minLines, int maxLines,
                           double minInterest, double maxInterest) {
        public Sequence(List<ResourceLocation> moves, double weight, int minLines, int maxLines) {
            this(moves, weight, minLines, maxLines, 0, 1);
        }
        public boolean fits(int lines) {
            return lines >= minLines && (maxLines < 0 || lines <= maxLines);
        }
        public boolean fits(int lines, double interest) {
            return fits(lines) && interest >= minInterest && interest <= maxInterest;
        }
    }
    public record Chattiness(double base, double perFamiliarity, int maxFamiliarity, Map<String, Double> personality) {
        public double of(int familiarity, List<String> personalities) {
            double mod = 0;
            for (String p : personalities) mod += personality.getOrDefault(p, 0D);
            if (!personalities.isEmpty()) mod /= personalities.size();
            return Math.max(0.05, Math.min(0.9, base + Math.min(familiarity, maxFamiliarity) * perFamiliarity + mod));
        }
    }
    public record Pause(double chance, double remarkChance, @Nullable ResourceLocation remarkMove) {
        public static final Pause NONE = new Pause(0, 0, null);
    }

    static ConversationEncounter parse(ResourceLocation id, JsonObject json) {
        Json.only(json, "schema", "context", "opening", "opening_again", "topics", "chattiness", "expansions", "pause",
                "bridge_pool", "closing", "mods");
        Context context = ConversationMove.enumValue(Context.class, GsonHelper.getAsString(json, "context"), "context");
        JsonObject topics = json.has("topics") ? GsonHelper.getAsJsonObject(json, "topics") : new JsonObject();
        Json.only(topics, "min", "max", "until");
        boolean until = "departure".equals(GsonHelper.getAsString(topics, "until", ""));
        int min = GsonHelper.getAsInt(topics, "min", 1), max = GsonHelper.getAsInt(topics, "max", until ? 64 : 3);
        if (min < 0 || max < Math.max(1, min)) throw Json.bad("topics", "needs 0 <= min <= max and max >= 1");
        JsonObject chat = json.has("chattiness") ? GsonHelper.getAsJsonObject(json, "chattiness") : new JsonObject();
        Json.only(chat, "base", "per_familiarity", "max_familiarity", "personality");
        Chattiness chattiness = new Chattiness(Json.number(chat, "base", 0.3, 0, 1), Json.number(chat, "per_familiarity", 0.02, 0, 1),
                GsonHelper.getAsInt(chat, "max_familiarity", 10), Json.numbers(chat, "personality", -1, 1));
        JsonObject expansions = json.has("expansions") ? GsonHelper.getAsJsonObject(json, "expansions") : new JsonObject();
        Json.only(expansions, "max");
        Pause pause = Pause.NONE;
        if (json.has("pause")) {
            JsonObject p = GsonHelper.getAsJsonObject(json, "pause");
            Json.only(p, "chance", "remark_chance", "remark_move");
            pause = new Pause(Json.number(p, "chance", 0, 0, 1), Json.number(p, "remark_chance", 0, 0, 1), Json.id(p, "remark_move"));
        }
        List<Sequence> closing = sequences(json, "closing");
        if (closing.isEmpty()) throw Json.bad("closing", "needs at least one sequence");
        List<Sequence> opening = sequences(json, "opening");
        if (opening.isEmpty()) throw Json.bad("opening", "needs at least one sequence");
        return new ConversationEncounter(id, context, opening, sequences(json, "opening_again"), min, max, until, chattiness,
                GsonHelper.getAsInt(expansions, "max", 2), pause, Json.id(json, "bridge_pool"), closing);
    }

    private static List<Sequence> sequences(JsonObject json, String key) {
        if (!json.has(key)) return List.of();
        List<Sequence> out = new ArrayList<>();
        var array = GsonHelper.getAsJsonArray(json, key);
        for (int i = 0; i < array.size(); i++) {
            JsonObject s = array.get(i).getAsJsonObject();
            Json.only(s, "sequence", "weight", "min_lines", "max_lines", "min_interest", "max_interest");
            out.add(new Sequence(Json.ids(s, "sequence"), Json.number(s, "weight", 1, 0.0001, 100),
                    GsonHelper.getAsInt(s, "min_lines", 0), GsonHelper.getAsInt(s, "max_lines", -1),
                    Json.number(s, "min_interest", 0, 0, 1), Json.number(s, "max_interest", 1, 0, 1)));
        }
        return List.copyOf(out);
    }
}
