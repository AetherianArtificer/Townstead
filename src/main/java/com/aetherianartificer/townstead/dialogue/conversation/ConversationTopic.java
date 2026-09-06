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

/** An authored, bounded conversation graph. Activities may suggest topics but never own them. */
public record ConversationTopic(ResourceLocation id, double weight, Gate gate, String start,
                                Map<String, Turn> turns) {
    public static final String SCHEMA = "townstead:conversation/v1";
    public enum Speaker { INITIATOR, RESPONDER }
    public record RelationshipChange(String quality, float amount, int halfLifeDays) {}
    public record Outcome(String memory, int initiatorOpinion, int responderOpinion,
                          int initiatorMood, int responderMood, String initiatorMemory, String responderMemory,
                          List<RelationshipChange> initiatorRelationship, List<RelationshipChange> responderRelationship) {
        public Outcome {
            initiatorRelationship = List.copyOf(initiatorRelationship);
            responderRelationship = List.copyOf(responderRelationship);
        }
        public Outcome(String memory, int initiatorOpinion, int responderOpinion, int initiatorMood, int responderMood) {
            this(memory, initiatorOpinion, responderOpinion, initiatorMood, responderMood, memory, memory, List.of(), List.of());
        }
        public Outcome(String memory, int initiatorOpinion, int responderOpinion, int initiatorMood, int responderMood,
                       String initiatorMemory, String responderMemory) {
            this(memory, initiatorOpinion, responderOpinion, initiatorMood, responderMood,
                    initiatorMemory, responderMemory, List.of(), List.of());
        }
    }
    public record Cue(ResourceLocation performance, ResourceLocation expression) {}
    public record Turn(String id, Speaker speaker, List<String> lines, int duration,
                       Gate gate, Cue gesture, Cue listener, List<String> replies, Outcome outcome) {}

    /** Eligibility stays separate from soft personality preferences. `when` is ordinary Pheno. */
    public record Gate(Set<String> context, Set<String> relationships,
                       Map<String, Double> personalityWeights, Map<String, Double> relationshipWeights, Condition when, Value evaluation) {
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
    }

    public static ConversationTopic parse(ResourceLocation id, JsonObject json) {
        only(json, "schema", "weight", "context", "relationship", "personality_weights", "relationship_weights", "when", "evaluation", "start", "turns");
        if (!SCHEMA.equals(GsonHelper.getAsString(json, "schema", ""))) throw bad("schema", "must be " + SCHEMA);
        String start = GsonHelper.getAsString(json, "start", "open");
        Map<String, Turn> turns = new LinkedHashMap<>();
        JsonObject nodes = GsonHelper.getAsJsonObject(json, "turns");
        if (nodes.size() < 2 || nodes.size() > 32) throw bad("turns", "must contain 2..32 turns");
        for (var entry : nodes.entrySet()) {
            String path = "turns." + entry.getKey();
            try {
                JsonObject node = entry.getValue().getAsJsonObject();
                only(node, "speaker", "lines", "duration", "context", "relationship", "personality_weights", "relationship_weights", "when", "evaluation", "gesture", "listener", "replies", "outcome");
                Speaker speaker = Speaker.valueOf(GsonHelper.getAsString(node, "speaker").toUpperCase(Locale.ROOT));
                List<String> lines = strings(node, "lines");
                if (lines.isEmpty() || lines.size() > 16) throw bad("lines", "must contain 1..16 localization keys");
                for (String line : lines) if (!line.contains(".") || line.chars().anyMatch(Character::isWhitespace))
                    throw bad("lines", "expected localization key: " + line);
                List<String> replies = strings(node, "replies");
                Outcome outcome = null;
                if (node.has("outcome")) {
                    JsonObject end = node.getAsJsonObject("outcome");
                    only(end, "memory", "initiator_memory", "responder_memory", "initiator_opinion", "responder_opinion",
                            "initiator_mood", "responder_mood", "initiator_relationship", "responder_relationship");
                    String memory = GsonHelper.getAsString(end, "memory");
                    if (ResourceLocation.tryParse(memory) == null) throw bad("outcome.memory", "expected resource id");
                    String initiatorMemory = GsonHelper.getAsString(end, "initiator_memory", memory);
                    String responderMemory = GsonHelper.getAsString(end, "responder_memory", memory);
                    if (ResourceLocation.tryParse(initiatorMemory) == null || ResourceLocation.tryParse(responderMemory) == null)
                        throw bad("outcome", "participant memories must be resource ids");
                    outcome = new Outcome(memory, delta(end, "initiator_opinion"), delta(end, "responder_opinion"),
                            delta(end, "initiator_mood"), delta(end, "responder_mood"), initiatorMemory, responderMemory,
                            relationshipChanges(end, "initiator_relationship"), relationshipChanges(end, "responder_relationship"));
                }
                if (replies.isEmpty() == (outcome == null)) throw bad("replies", "supply replies OR a terminal outcome");
                turns.put(entry.getKey(), new Turn(entry.getKey(), speaker, lines, duration(node), gate(node),
                        cue(node, "gesture"), cue(node, "listener"), replies, outcome));
            } catch (RuntimeException ex) { throw bad(path, ex.getMessage()); }
        }
        if (!turns.containsKey(start)) throw bad("start", "missing turn " + start);
        Set<String> reached = new HashSet<>();
        validate(start, turns, new HashSet<>(), reached, 0);
        if (reached.size() != turns.size()) throw bad("turns", "contains unreachable turns");
        return new ConversationTopic(id, positive(json, "weight", 1), gate(json), start,
                Collections.unmodifiableMap(turns));
    }

    private static void validate(String id, Map<String, Turn> turns, Set<String> path, Set<String> reached, int depth) {
        Turn turn = turns.get(id);
        if (turn == null) throw bad("replies", "missing turn " + id);
        if (!path.add(id)) throw bad("replies", "cycle at " + id);
        if (depth >= 8) throw bad("replies", "paths must contain at most eight turns");
        reached.add(id);
        if (turn.outcome() != null && path.stream().map(node -> turns.get(node).speaker()).distinct().count() < 2)
            throw bad("outcome", "both participants must speak before an outcome");
        for (String reply : turn.replies()) validate(reply, turns, path, reached, depth + 1);
        path.remove(id);
    }

    private static Gate gate(JsonObject json) {
        Map<String, Double> weights = weights(json, "personality_weights");
        Condition when = null;
        if (json.has("when")) {
            when = Conditions.parse(PhenoNormalizer.normalizeCondition(json.getAsJsonObject("when")));
            if (when == null) throw bad("when", "invalid Pheno condition");
        }
        Value evaluation = json.has("evaluation") ? Values.parse(PhenoNormalizer.normalizeValue(json.get("evaluation"))) : null;
        if (json.has("evaluation") && evaluation == null) throw bad("evaluation", "invalid Pheno value");
        return new Gate(Set.copyOf(strings(json, "context")), Set.copyOf(strings(json, "relationship")), weights,
                weights(json, "relationship_weights"), when, evaluation);
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

    private static Cue cue(JsonObject json, String field) {
        if (!json.has(field)) return new Cue(null, null);
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
    private static List<String> strings(JsonObject json, String key) {
        if (!json.has(key)) return List.of();
        List<String> out = new ArrayList<>();
        JsonElement value = json.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) return List.of(value.getAsString());
        for (JsonElement e : value.getAsJsonArray()) out.add(GsonHelper.convertToString(e, key));
        return List.copyOf(out);
    }
    private static int duration(JsonObject json) {
        JsonElement value = json.get("duration");
        double ticks = 70;
        if (value != null) {
            String raw = value.getAsString().trim();
            double unit = raw.endsWith("s") ? 20 : 1;
            ticks = Double.parseDouble(raw.replaceAll("[st]$", "")) * unit;
        }
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
    private static void only(JsonObject json, String... fields) {
        Set<String> allowed = Set.of(fields);
        for (String field : json.keySet()) if (!allowed.contains(field)) throw bad(field, "unknown field");
    }
    private static IllegalArgumentException bad(String field, String message) {
        return new IllegalArgumentException(field + ": " + message);
    }
}
