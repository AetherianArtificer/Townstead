package com.aetherianartificer.townstead.dialogue.conversation;

import com.aetherianartificer.townstead.dialogue.contextual.DialogueRequest;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.lang.normalize.PhenoNormalizer;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.*;
import java.util.function.Predicate;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;

/**
 * One subject's graph of moves. It has no greeting or goodbye: the encounter adds those. A turn
 * names a move and lets the composer build the line, or carries authored {@code lines} as a set piece.
 */
public record ConversationTopic(ResourceLocation id, double weight, Gate gate, String start,
                                Map<String, Turn> turns, List<String> subjects, String register,
                                List<String> associated, List<ResourceLocation> expansionMoves, int expansionMax,
                                Map<String, Double> interests, List<String> requires, Set<String> contentTags) {
    public static final String SCHEMA = "townstead:conversation/v1";
    public static final ResourceLocation SAY = ResourceLocation.tryParse("townstead:say");
    public enum Speaker { INITIATOR, RESPONDER }
    public record RelationshipChange(String quality, float amount, int halfLifeDays) {}
    public record Outcome(String memory, int initiatorOpinion, int responderOpinion,
                          int initiatorMood, int responderMood, String initiatorMemory, String responderMemory,
                          List<RelationshipChange> initiatorRelationship, List<RelationshipChange> responderRelationship,
                          boolean spread) {
        public Outcome {
            initiatorRelationship = List.copyOf(initiatorRelationship);
            responderRelationship = List.copyOf(responderRelationship);
        }
        public Outcome(String memory, int initiatorOpinion, int responderOpinion, int initiatorMood, int responderMood) {
            this(memory, initiatorOpinion, responderOpinion, initiatorMood, responderMood, memory, memory, List.of(), List.of(), true);
        }
        public Outcome(String memory, int initiatorOpinion, int responderOpinion, int initiatorMood, int responderMood,
                       String initiatorMemory, String responderMemory) {
            this(memory, initiatorOpinion, responderOpinion, initiatorMood, responderMood,
                    initiatorMemory, responderMemory, List.of(), List.of(), true);
        }
    }
    public record Cue(ResourceLocation performance, ResourceLocation expression) {
        public static final Cue NONE = new Cue(null, null);
        public boolean isEmpty() { return performance == null && expression == null; }
    }
    /** {@code duration} is in ticks; -1 lets the engine time the line by its length. */
    public record Turn(String id, Speaker speaker, ResourceLocation move, List<String> lines, int duration,
                       Gate gate, Cue gesture, Cue listener, List<String> replies, double weight, String register,
                       Outcome outcome, Set<String> contentTags) {
        public boolean terminal() { return replies.isEmpty(); }
    }

    /** Eligibility stays separate from soft personality preferences. `when` is ordinary Pheno. */
    public record Gate(Set<String> context, Set<String> relationships,
                       Map<String, Double> personalityWeights, Map<String, Double> relationshipWeights, Condition when, Value evaluation) {
        public static final Gate OPEN = new Gate(Set.of(), Set.of(), Map.of(), Map.of(), null, null);
        public double weight(DialogueRequest request, Predicate<Condition> test) {
            return weight(request, test, value -> Double.NaN);
        }
        public double weight(DialogueRequest request, Predicate<Condition> test, java.util.function.ToDoubleFunction<Value> evaluate) {
            if (!request.context().containsAll(context)
                    || !request.relationship().containsAll(relationships)
                    || when != null && !test.test(when)) return 0;
            double value = personalityWeights.getOrDefault("default", 1D);
            for (String personality : request.personality()) {
                Double match = personalityWeights.get(personality);
                if (match == null && personality.startsWith("mca:")) match = personalityWeights.get(personality.substring(4));
                if (match != null) { value = match; break; }
            }
            boolean matched = false;
            for (String relationship : request.relationship()) {
                Double modifier = relationshipWeights.get(relationship);
                if (modifier != null) { value *= modifier; matched = true; }
            }
            double score = evaluation == null ? 1 : evaluate.applyAsDouble(evaluation);
            if (!Double.isFinite(score) || score <= 0) return 0;
            return (matched ? value : value * relationshipWeights.getOrDefault("default", 1D)) * Math.min(100, score);
        }
        public boolean isOpen() {
            return context.isEmpty() && relationships.isEmpty() && personalityWeights.isEmpty()
                    && relationshipWeights.isEmpty() && when == null && evaluation == null;
        }
    }

    static final Set<String> GATE_FIELDS = Set.of("context", "relationship", "personality_weights", "relationship_weights", "when", "evaluation");

    public static ConversationTopic parse(ResourceLocation id, JsonObject json) {
        only(json, "schema", "weight", "start", "turns", "subjects", "register", "associated", "expansions", "interests",
                "requires", "content_tags", "context", "relationship", "personality_weights", "relationship_weights", "when", "evaluation");
        if (!SCHEMA.equals(GsonHelper.getAsString(json, "schema", ""))) throw bad("schema", "must be " + SCHEMA);
        JsonObject nodes = GsonHelper.getAsJsonObject(json, "turns");
        if (nodes.size() < 1 || nodes.size() > 32) throw bad("turns", "must contain 1..32 turns");
        String start = GsonHelper.getAsString(json, "start", nodes.keySet().iterator().next());
        Map<String, Turn> turns = new LinkedHashMap<>();
        for (var entry : nodes.entrySet()) {
            String path = "turns." + entry.getKey();
            try {
                JsonObject node = entry.getValue().getAsJsonObject();
                only(node, "speaker", "move", "lines", "duration", "weight", "register", "gesture", "listener", "replies",
                        "outcome", "content_tags", "context", "relationship", "personality_weights", "relationship_weights",
                        "when", "evaluation");
                Speaker speaker = Speaker.valueOf(GsonHelper.getAsString(node, "speaker").toUpperCase(Locale.ROOT));
                ResourceLocation move = id(node, "move");
                if (move == null) throw bad("move", "is required");
                List<String> lines = strings(node, "lines");
                if (lines.size() > 16) throw bad("lines", "must contain at most 16 localization keys");
                for (String line : lines) if (!line.contains(".") || line.chars().anyMatch(Character::isWhitespace))
                    throw bad("lines", "expected localization key: " + line);
                List<String> replies = strings(node, "replies");
                Outcome outcome = node.has("outcome") ? outcome(node.getAsJsonObject("outcome")) : null;
                if (outcome != null && !replies.isEmpty()) throw bad("outcome", "only a turn without replies can carry an outcome");
                turns.put(entry.getKey(), new Turn(entry.getKey(), speaker, move, lines, duration(node), gate(node),
                        cue(node, "gesture"), cue(node, "listener"), replies, positive(node, "weight", 1),
                        GsonHelper.getAsString(node, "register", ""), outcome, Set.copyOf(strings(node, "content_tags"))));
            } catch (RuntimeException ex) { throw bad(path, ex.getMessage()); }
        }
        if (!turns.containsKey(start)) throw bad("start", "missing turn " + start);
        Set<String> reached = new HashSet<>();
        validate(start, turns, new ArrayList<>(), reached, 0);
        if (reached.size() != turns.size()) throw bad("turns", "contains unreachable turns");
        List<ResourceLocation> expansionMoves = new ArrayList<>();
        int expansionMax = 0;
        if (json.has("expansions")) {
            JsonObject expansions = GsonHelper.getAsJsonObject(json, "expansions");
            only(expansions, "moves", "max");
            for (String move : strings(expansions, "moves")) {
                ResourceLocation parsed = ResourceLocation.tryParse(move);
                if (parsed == null) throw bad("expansions.moves", "expected resource id: " + move);
                expansionMoves.add(parsed);
            }
            expansionMax = GsonHelper.getAsInt(expansions, "max", 2);
            if (expansionMax < 0 || expansionMax > 6) throw bad("expansions.max", "must be 0..6");
        }
        Map<String, Double> interests = new LinkedHashMap<>();
        if (json.has("interests")) for (var e : GsonHelper.getAsJsonObject(json, "interests").entrySet())
            interests.put(e.getKey(), e.getValue().getAsDouble());
        return new ConversationTopic(id, positive(json, "weight", 1), gate(json), start,
                Collections.unmodifiableMap(turns), strings(json, "subjects"), GsonHelper.getAsString(json, "register", "ambient"),
                strings(json, "associated"), List.copyOf(expansionMoves), expansionMax, Map.copyOf(interests),
                strings(json, "requires"), Set.copyOf(strings(json, "content_tags")));
    }

    private static void validate(String id, Map<String, Turn> turns, List<String> path, Set<String> reached, int depth) {
        Turn turn = turns.get(id);
        if (turn == null) throw bad("replies", "missing turn " + id);
        if (path.contains(id)) throw bad("replies", "cycle at " + id);
        if (depth >= 8) throw bad("replies", "paths must contain at most eight turns");
        path.add(id);
        reached.add(id);
        if (turn.outcome() != null && path.stream().map(node -> turns.get(node).speaker()).distinct().count() < 2)
            throw bad("outcome", "both participants must speak before an outcome");
        for (String reply : turn.replies()) validate(reply, turns, path, reached, depth + 1);
        path.remove(path.size() - 1);
    }

    static Outcome outcome(JsonObject end) {
        only(end, "memory", "initiator_memory", "responder_memory", "initiator_opinion", "responder_opinion",
                "initiator_mood", "responder_mood", "initiator_relationship", "responder_relationship", "spread");
        String memory = GsonHelper.getAsString(end, "memory");
        if (ResourceLocation.tryParse(memory) == null) throw bad("outcome.memory", "expected resource id");
        String initiatorMemory = GsonHelper.getAsString(end, "initiator_memory", memory);
        String responderMemory = GsonHelper.getAsString(end, "responder_memory", memory);
        if (ResourceLocation.tryParse(initiatorMemory) == null || ResourceLocation.tryParse(responderMemory) == null)
            throw bad("outcome", "participant memories must be resource ids");
        return new Outcome(memory, delta(end, "initiator_opinion"), delta(end, "responder_opinion"),
                delta(end, "initiator_mood"), delta(end, "responder_mood"), initiatorMemory, responderMemory,
                relationshipChanges(end, "initiator_relationship"), relationshipChanges(end, "responder_relationship"),
                GsonHelper.getAsBoolean(end, "spread", true));
    }

    /** Parses the shared gate fields of any conversation document. */
    public static Gate gate(JsonObject json) {
        Map<String, Double> weights = weights(json, "personality_weights");
        Condition when = null;
        if (json.has("when")) {
            when = Conditions.parse(PhenoNormalizer.normalizeCondition(json.getAsJsonObject("when")));
            if (when == null) throw bad("when", "invalid Pheno condition");
        }
        Value evaluation = json.has("evaluation") ? Values.parse(PhenoNormalizer.normalizeValue(json.get("evaluation"))) : null;
        if (json.has("evaluation") && evaluation == null) throw bad("evaluation", "invalid Pheno value");
        Map<String, Double> relationshipWeights = weights(json, "relationship_weights");
        Set<String> context = Set.copyOf(strings(json, "context"));
        Set<String> relationships = Set.copyOf(strings(json, "relationship"));
        if (context.isEmpty() && relationships.isEmpty() && weights.isEmpty() && relationshipWeights.isEmpty()
                && when == null && evaluation == null) return Gate.OPEN;
        return new Gate(context, relationships, weights, relationshipWeights, when, evaluation);
    }
    private static Map<String, Double> weights(JsonObject json, String field) {
        Map<String, Double> weights = new LinkedHashMap<>();
        if (json.has(field)) for (var e : json.getAsJsonObject(field).entrySet()) {
            double value = e.getValue().getAsDouble();
            if (!Double.isFinite(value) || value <= 0 || value > 100) throw bad(field, "weights must be > 0 and <= 100");
            weights.put(e.getKey().toLowerCase(Locale.ROOT), value);
        }
        return Map.copyOf(weights);
    }

    public static Cue cue(JsonObject json, String field) {
        if (!json.has(field)) return Cue.NONE;
        JsonObject value = json.getAsJsonObject(field);
        only(value, "performance", "expression");
        return new Cue(id(value, "performance"), id(value, "expression"));
    }
    private static ResourceLocation id(JsonObject json, String key) {
        if (!json.has(key)) return null;
        ResourceLocation id = ResourceLocation.tryParse(json.get(key).getAsString());
        if (id == null) throw bad(key, "expected resource id");
        return id;
    }
    static List<String> strings(JsonObject json, String key) {
        if (!json.has(key)) return List.of();
        List<String> out = new ArrayList<>();
        JsonElement value = json.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) return List.of(value.getAsString());
        for (JsonElement e : value.getAsJsonArray()) out.add(GsonHelper.convertToString(e, key));
        return List.copyOf(out);
    }
    private static int duration(JsonObject json) {
        JsonElement value = json.get("duration");
        if (value == null) return -1;
        String raw = value.getAsString().trim();
        double unit = raw.endsWith("s") ? 20 : 1;
        double ticks = Double.parseDouble(raw.replaceAll("[st]$", "")) * unit;
        if (!Double.isFinite(ticks) || ticks < 20 || ticks > 200) throw bad("duration", "must be 20..200 ticks or 1s..10s");
        return (int) Math.round(ticks);
    }
    private static int delta(JsonObject json, String field) {
        int value = GsonHelper.getAsInt(json, field, 0);
        if (value < -4 || value > 4) throw bad(field, "must be -4..4");
        return value;
    }
    private static List<RelationshipChange> relationshipChanges(JsonObject json, String field) {
        if (!json.has(field)) return List.of();
        JsonArray array = GsonHelper.getAsJsonArray(json, field);
        List<RelationshipChange> changes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            JsonObject value = array.get(i).getAsJsonObject();
            only(value, "quality", "amount", "half_life_days");
            String quality = GsonHelper.getAsString(value, "quality");
            if (ResourceLocation.tryParse(quality) == null) throw bad(field + "[" + i + "].quality", "expected resource id");
            if (!seen.add(quality)) throw bad(field, "contains duplicate quality " + quality);
            float amount = GsonHelper.getAsFloat(value, "amount");
            if (!Float.isFinite(amount) || amount < -100 || amount > 100 || amount == 0)
                throw bad(field + "[" + i + "].amount", "must be nonzero and between -100 and 100");
            int halfLife = GsonHelper.getAsInt(value, "half_life_days", -1);
            if (halfLife < -1) throw bad(field + "[" + i + "].half_life_days", "must be >= 0");
            changes.add(new RelationshipChange(quality, amount, halfLife));
        }
        return List.copyOf(changes);
    }
    private static double positive(JsonObject json, String field, double fallback) {
        double value = GsonHelper.getAsDouble(json, field, fallback);
        if (!Double.isFinite(value) || value <= 0 || value > 100) throw bad(field, "must be > 0 and <= 100");
        return value;
    }
    static void only(JsonObject json, String... fields) {
        Set<String> allowed = Set.of(fields);
        for (String field : json.keySet()) if (!allowed.contains(field)) throw bad(field, "unknown field");
    }
    static IllegalArgumentException bad(String field, String message) {
        return new IllegalArgumentException(field + ": " + message);
    }
}
